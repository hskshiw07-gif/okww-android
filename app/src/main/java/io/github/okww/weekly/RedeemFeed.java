package io.github.okww.weekly;

import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Same public source as PC RedeemCodeService; no UID, credentials, cookies or account files are sent. */
final class RedeemFeed {
    static final String SOURCE="https://newsimg.5054399.com/comm/mlcxqcommon/static/wap/js/data_102.js";
    static List<RedeemCodes.Code> fetch()throws Exception{
        HttpsURLConnection connection=(HttpsURLConnection)new URL(SOURCE+"?_="+System.currentTimeMillis()).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/135.0.0.0 Mobile Safari/537.36");
        connection.setRequestProperty("Referer","https://news.4399.com/mlcx/");
        try{
            if(connection.getResponseCode()!=200)throw new IOException("公开码源响应异常："+connection.getResponseCode());
            try(InputStream input=connection.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int size;
                long until=System.nanoTime()+25000000000L;
                while((size=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    if(out.size()+size>1000000||System.nanoTime()>until)throw new IOException("公开码源过大或超时");out.write(buffer,0,size);
                }
                return parse(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{connection.disconnect();}
    }
    static String payload(String input){
        if(input==null||input.length()>1000000)throw new IllegalArgumentException("公开码源为空或过大");
        int begin=input.indexOf('['),end=input.lastIndexOf(']');
        if(begin<0||end<begin)throw new IllegalArgumentException("公开码源不是兑换码数组");
        return input.substring(begin,end+1); // Parse JSON only; never evaluate JavaScript/JSONP.
    }
    static List<RedeemCodes.Code> parse(String input)throws Exception{
        JSONArray array=new JSONArray(payload(input));if(array.length()>RedeemCodes.LIMIT)throw new IllegalArgumentException("公开码源条目过多");
        List<RedeemCodes.Code> result=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            JSONObject row=array.getJSONObject(i);String code=row.optString("order","");if(code.isEmpty())code=row.optString("code","");
            if(code.trim().isEmpty())continue;
            result.add(new RedeemCodes.Code(code,row.optString("reward",""),row.optString("label",""),false,
                    row.optString("is_fail",row.optString("isFail","0")).trim().equals("1"),true));
        }
        return result;
    }
}
