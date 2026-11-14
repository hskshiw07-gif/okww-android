package io.github.okww.weekly;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** PC redeem-center catalog semantics; remote discoveries require explicit local enablement. */
final class RedeemCodes {
    static final int LIMIT=200;
    static final class Code {
        final String code,reward,label;
        final boolean enabled,failed,remote;
        Code(String code,String reward,String label,boolean enabled,boolean failed,boolean remote){
            this.code=normalize(code);this.reward=field(reward);this.label=field(label);
            this.enabled=enabled;this.failed=failed;this.remote=remote;
        }
        boolean invalid(){return failed||code.equals("MINGCHAO");}
        boolean available(long now){return enabled&&!invalid()&&!expired(label,now);}
        Code enabled(boolean value){return new Code(code,reward,label,value,failed,remote);}
    }
    final LinkedHashMap<String,Code> codes=new LinkedHashMap<>();
    long fetchedAt;
    static String normalize(String input){
        String code=input==null?"":input.trim().toUpperCase(Locale.ROOT);
        if(!code.matches("[A-Z0-9_-]{4,64}"))throw new IllegalArgumentException("兑换码须为4—64位字母、数字、下划线或短横线");
        return code;
    }
    static String field(String input){
        if(input==null||input.length()>600||input.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'))
            throw new IllegalArgumentException("兑换码说明格式异常");
        return input;
    }
    void manual(String input){
        if(input==null||input.length()>16000)throw new IllegalArgumentException("兑换码输入过长");
        LinkedHashSet<String> parsed=new LinkedHashSet<>();
        for(String item:input.split("[\\s,，;；]+"))if(!item.isEmpty())parsed.add(normalize(item));
        if(parsed.isEmpty())throw new IllegalArgumentException("请填写至少一个兑换码");
        Set<String> combined=new HashSet<>(codes.keySet());combined.addAll(parsed);
        if(combined.size()>LIMIT)throw new IllegalArgumentException("兑换码缓存最多200项");
        for(String code:parsed)codes.putIfAbsent(code,new Code(code,"","",true,false,false));
    }
    void merge(List<Code> incoming,long now){
        if(incoming.size()>LIMIT||now<0||now<fetchedAt)throw new IllegalArgumentException("码源数量或时间异常");
        LinkedHashMap<String,Code> next=new LinkedHashMap<>(codes);Set<String> seen=new HashSet<>();
        for(Code value:incoming){
            if(!seen.add(value.code))throw new IllegalArgumentException("公共码源存在重复兑换码");
            Code old=next.get(value.code);
            next.put(value.code,new Code(value.code,value.reward,value.label,old!=null&&old.enabled&&!value.invalid(),value.failed,true));
        }
        if(next.size()>LIMIT)throw new IllegalArgumentException("兑换码缓存超过200项，请先移除旧码");
        codes.clear();codes.putAll(next);fetchedAt=now;
    }
    List<String> selected(long now){
        List<String> result=new ArrayList<>();for(Code code:codes.values())if(code.available(now))result.add(code.code);return result;
    }
    static boolean expired(String label,long now){
        ZonedDateTime current=Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai"));
        Matcher date=Pattern.compile("(?:(20[0-9]{2})[年/.-])?([0-9]{1,2})\\s*[月/.-]\\s*([0-9]{1,2})").matcher(label);
        if(!date.find())return false;
        int year=date.group(1)==null?current.getYear():Integer.parseInt(date.group(1));
        Matcher time=Pattern.compile("([0-9]{1,2})\\s*[:点时]\\s*([0-9]{1,2})?").matcher(label);
        int hour=23,minute=59;if(time.find()){hour=Integer.parseInt(time.group(1));minute=time.group(2)==null?0:Integer.parseInt(time.group(2));}
        try{return current.isAfter(ZonedDateTime.of(year,Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3)),hour,minute,59,0,current.getZone()));}
        catch(DateTimeException invalid){return true;}
    }
    String save(){
        StringBuilder out=new StringBuilder("1\t").append(fetchedAt);
        for(Code c:codes.values())out.append('\n').append(c.code).append('\t').append(c.enabled?1:0).append('\t').append(c.failed?1:0)
                .append('\t').append(c.remote?1:0).append('\t').append(encode(c.reward)).append('\t').append(encode(c.label));
        return out.toString();
    }
    static RedeemCodes parse(String saved){
        RedeemCodes result=new RedeemCodes();if(saved==null)return result;
        if(saved.length()>400000)throw new IllegalArgumentException("兑换码缓存过大");
        String[] rows=saved.split("\n",-1),header=rows[0].split("\t",-1);
        if(header.length!=2||!header[0].equals("1"))throw new IllegalArgumentException("兑换码缓存版本异常，保留文件");
        result.fetchedAt=Long.parseLong(header[1]);if(result.fetchedAt<0||rows.length>LIMIT+1)throw new IllegalArgumentException("兑换码缓存异常");
        for(int i=1;i<rows.length;i++){
            String[] f=rows[i].split("\t",-1);
            if(f.length!=6||!f[1].matches("[01]")||!f[2].matches("[01]")||!f[3].matches("[01]"))throw new IllegalArgumentException("兑换码行格式异常");
            Code code=new Code(f[0],decode(f[4]),decode(f[5]),f[1].equals("1"),f[2].equals("1"),f[3].equals("1"));
            if(result.codes.put(code.code,code)!=null)throw new IllegalArgumentException("兑换码缓存重复");
        }
        return result;
    }
    static String encode(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    static String decode(String value){return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
}
