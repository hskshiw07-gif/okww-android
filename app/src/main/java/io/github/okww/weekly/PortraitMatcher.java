package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** RGB normalized correlation of the existing PC portrait exports in mobile standby slots. */
final class PortraitMatcher {
    static final class Pixels {
        final int width, height;
        final int[] argb;
        Pixels(int width, int height, int[] argb) {
            if (width <= 0 || height <= 0 || argb == null || (long) width * height != argb.length)
                throw new IllegalArgumentException("无效图像像素");
            this.width = width; this.height = height; this.argb = argb;
        }
    }
    static final class Template {
        final String character;
        final Pixels pixels;
        final int sourceHeight;
        final double[] normalized;
        Template(String character, Pixels pixels, int sourceHeight) {
            if (character == null || pixels == null || sourceHeight <= 0) throw new IllegalArgumentException("头像模板异常");
            this.character = character; this.pixels = pixels; this.sourceHeight = sourceHeight;
            normalized = vector(pixels, 0, 0, pixels.width, pixels.height);
        }
    }
    static final class Match {
        final String character;
        final double score, runnerUp;
        final float x, y;
        Match(String character, double score, double runnerUp, float x, float y) {
            this.character = character; this.score = score; this.runnerUp = runnerUp; this.x = x; this.y = y;
        }
        boolean reliable() { return score >= .83 && score - runnerUp >= .08; }
    }
    private static final int GRID_X = 12, GRID_Y = 14;

    static Match mobileStandby(Pixels frame,List<Template> templates,int slot) {
        if(frame.width!=2400||frame.height!=1080||slot<0||slot>1)throw new IllegalArgumentException("待机头像布局未标定");
        // Measured avatar centers 2169..2176,273..276 / 413..414; all 66 templates still compete.
        return match(frame,templates,2125,slot==0?215:355,2230,slot==0?325:470);
    }

    static Match[] mobileStandbyPair(Pixels frame,List<Template> templates,List<Template> grayTemplates) {
        Match[] result={mobileStandby(frame,templates,0),mobileStandby(frame,templates,1)};
        if(!result[0].reliable()||!result[1].reliable()) {
            // Dev28 combat desaturates standby portraits; keep reliable RGB matches and compete against every gray template.
            Pixels gray=grayscale(frame);
            for(int slot=0;slot<2;slot++)if(!result[slot].reliable())result[slot]=mobileStandby(gray,grayTemplates,slot);
        }
        return result;
    }

    static List<Template> grayscaleTemplates(List<Template> templates) {
        List<Template> result=new ArrayList<>();
        for(Template template:templates)result.add(new Template(template.character,grayscale(template.pixels),template.sourceHeight));
        return result;
    }

    static Pixels grayscale(Pixels source) {
        int[] gray=new int[source.argb.length];
        for(int i=0;i<gray.length;i++) {
            int c=source.argb[i],value=(77*(c>>16&255)+150*(c>>8&255)+29*(c&255)+128)>>8;
            gray[i]=(c&0xff000000)|(value<<16)|(value<<8)|value;
        }
        return new Pixels(source.width,source.height,gray);
    }

    static Match match(Pixels frame, List<Template> templates, int left, int top, int right, int bottom) {
        return match(frame, templates, left, top, right, bottom, 1.25);
    }
    static Match match(Pixels frame, List<Template> templates, int left, int top, int right, int bottom, double uiScale) {
        if (!Double.isFinite(uiScale) || uiScale <= 0 || uiScale > 3) throw new IllegalArgumentException("模板缩放异常");
        if (templates == null || templates.isEmpty() || left < 0 || top < 0 || right > frame.width || bottom > frame.height
                || left >= right || top >= bottom) throw new IllegalArgumentException("头像搜索区域异常");
        Map<String, Match> best = new HashMap<>();
        for (Template template : templates) {
            // shortcut: avatar UI scale 1.25 was observed on Xiaomi 13 Pro; other HUD scales need calibration.
            double scale = frame.height / (double) template.sourceHeight * uiScale;
            int width = (int) Math.round(template.pixels.width * scale), height = (int) Math.round(template.pixels.height * scale);
            if (width < GRID_X || height < GRID_Y || width > right - left || height > bottom - top) continue;
            double[] normalized = template.normalized;
            if (normalized == null) continue;
            int[] sampleX = offsets(width, GRID_X), sampleY = offsets(height, GRID_Y);
            int bestX = left, bestY = top; double score = -1;
            for (int y = top; y <= bottom - height; y += 3) for (int x = left; x <= right - width; x += 3) {
                double current = correlate(frame, x, y, sampleX, sampleY, normalized);
                if (current > score) { score = current; bestX = x; bestY = y; }
            }
            int coarseX = bestX, coarseY = bestY;
            for (int y = Math.max(top, coarseY - 3); y <= Math.min(bottom - height, coarseY + 3); y++)
                for (int x = Math.max(left, coarseX - 3); x <= Math.min(right - width, coarseX + 3); x++) {
                    double current = correlate(frame, x, y, sampleX, sampleY, normalized);
                    if (current > score) { score = current; bestX = x; bestY = y; }
                }
            Match old = best.get(template.character);
            if (old == null || score > old.score) best.put(template.character, new Match(template.character, score, -1,
                    (bestX + width / 2f) / frame.width, (bestY + height / 2f) / frame.height));
        }
        List<Match> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingDouble((Match m) -> m.score).reversed());
        if (ranked.isEmpty()) return new Match("", -1, -1, 0, 0);
        Match first = ranked.get(0);
        return new Match(first.character, first.score, ranked.size() > 1 ? ranked.get(1).score : -1, first.x, first.y);
    }

    private static double[] vector(Pixels image, int left, int top, int width, int height) {
        double[] values = new double[GRID_X * GRID_Y * 3];
        double[] means = new double[3];
        int index = 0;
        for (int y = 0; y < GRID_Y; y++) for (int x = 0; x < GRID_X; x++) {
            int color = image.argb[(top + Math.round(y * (height - 1f) / (GRID_Y - 1))) * image.width
                    + left + Math.round(x * (width - 1f) / (GRID_X - 1))];
            for (int c = 0; c < 3; c++) { double value = (color >> (c * 8)) & 255; values[index++] = value; means[c] += value; }
        }
        for (int c = 0; c < 3; c++) means[c] /= GRID_X * GRID_Y;
        double norm = 0;
        for (int i = 0; i < values.length; i++) { values[i] -= means[i % 3]; norm += values[i] * values[i]; }
        if (norm < 1) return null;
        norm = Math.sqrt(norm);
        for (int i = 0; i < values.length; i++) values[i] /= norm;
        return values;
    }

    private static int[] offsets(int size, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = Math.round(i * (size - 1f) / (count - 1));
        return result;
    }

    private static double correlate(Pixels image, int left, int top, int[] sampleX, int[] sampleY, double[] template) {
        double sumB = 0, sumG = 0, sumR = 0, squared = 0, dot = 0;
        int index = 0;
        for (int y = 0; y < GRID_Y; y++) {
            int row = (top + sampleY[y]) * image.width + left;
            for (int x = 0; x < GRID_X; x++) {
                int color = image.argb[row + sampleX[x]];
                int b = color & 255, g = (color >> 8) & 255, r = (color >> 16) & 255;
                sumB += b; sumG += g; sumR += r; squared += b*b + g*g + r*r;
                dot += b * template[index++] + g * template[index++] + r * template[index++];
            }
        }
        double norm = squared - (sumB*sumB + sumG*sumG + sumR*sumR) / (GRID_X * GRID_Y);
        return norm > 1 ? Math.max(-1, Math.min(1, dot / Math.sqrt(norm))) : -1;
    }
}
