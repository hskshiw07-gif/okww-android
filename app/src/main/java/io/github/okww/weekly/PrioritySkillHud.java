package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.List;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

/** Original ok-ww feature labels, searched at mobile controls instead of desktop key boxes. */
final class PrioritySkillHud {
    private final List<PortraitMatcher.Template> templates;

    PrioritySkillHud(List<PortraitMatcher.Template> templates) {
        this.templates = templates;
    }

    boolean feature(PortraitMatcher.Pixels frame, String name, CombatTouchLayout.Control control) {
        return controlScore(frame,name,control)>=.70;
    }
    double controlScore(PortraitMatcher.Pixels frame,String name,CombatTouchLayout.Control control){
        requireFrame(frame);
        CombatGesture.Contact point = CombatTouchLayout.forXiaomi13Pro().point(control);
        int x = Math.round(point.x * 2400), y = Math.round(point.y * 1080);
        // The mobile attack icon is larger than E/R: real Hsin H1 needs 2.2x the PC half-height crop.
        return score(frame, templates, name, x - 75, y - 75, x + 75, y + 75,
                control==ATTACK?new double[]{.8,1,1.2,1.4,1.6,1.8,2,2.2,2.4,2.6,2.8}:new double[]{.8,1,1.2,1.4,1.6});
    }

    boolean actionIndicator(PortraitMatcher.Pixels frame) {
        requireFrame(frame);
        // BaseChar.has_long_action() matches has_target at .6; it does not compare no_target.
        return score(frame, templates, "has_target", 535, 945, 620, 1025) >= .60;
    }

    /** Mobile counterpart of the PC mouse/E key highlight: triangle AND control ring. */
    static boolean highlighted(PortraitMatcher.Pixels frame, CombatTouchLayout.Control control) {
        requireFrame(frame);
        if (control != ATTACK && control != RESONANCE) throw new IllegalArgumentException("不是强化提示按钮");
        CombatGesture.Contact point = CombatTouchLayout.forXiaomi13Pro().point(control);
        int cx = Math.round(point.x * 2400), cy = Math.round(point.y * 1080);
        int radius = control == ATTACK ? 84 : 62;
        int triangle = 0, ring = 0;
        for (int y = cy - radius - 7; y < cy - radius + 14; y++)
            for (int x = cx - 15; x <= cx + 15; x++) if (highlightColor(frame.argb[y * frame.width + x])) triangle++;
        for (int angle = 0; angle < 72; angle++) {
            double theta = angle * Math.PI / 36;
            boolean found = false;
            for (int r = radius - 5; r <= radius + 5; r++) {
                int x = cx + (int)Math.round(r * Math.cos(theta)), y = cy + (int)Math.round(r * Math.sin(theta));
                found |= highlightColor(frame.argb[y * frame.width + x]);
            }
            if (found) ring++;
        }
        return triangle >= 100 && ring >= 40;
    }

    private static boolean highlightColor(int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        // The native prompt uses the character's element: orange for Aemeath, ice blue for Hiyuki.
        return Math.max(r,Math.max(g,b))>=210 && Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>=35;
    }

    static double score(PortraitMatcher.Pixels frame, List<PortraitMatcher.Template> templates,
                        String name, int l, int t, int r, int b) {
        return score(frame,templates,name,l,t,r,b,new double[]{.8,1,1.2,1.4,1.6});
    }
    private static double score(PortraitMatcher.Pixels frame, List<PortraitMatcher.Template> templates,
                        String name, int l, int t, int r, int b,double[] scales) {
        List<PortraitMatcher.Template> selected = new ArrayList<>();
        for (PortraitMatcher.Template template : templates) if (template.character.equals(name)) selected.add(template);
        if (selected.isEmpty()) throw new IllegalStateException("原项目战斗特征缺失：" + name);
        double best = -1;
        for (double scale : scales)
            best = Math.max(best, PortraitMatcher.match(frame, selected, l, t, r, b, scale).score);
        return best;
    }

    private static void requireFrame(PortraitMatcher.Pixels frame) {
        if (frame.width != 2400 || frame.height != 1080) throw new IllegalStateException("角色特征画幅未标定");
    }
}
