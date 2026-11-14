package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Private login-list identifiers only. No passwords, tokens, workshop axes or remembered teams. */
final class MultiAccountPlan {
    static final class Account {
        final String login, uid;
        final int tacetIndex;
        final boolean enabled;
        Account(String login, String uid, int tacetIndex, boolean enabled) {
            if (login == null || login.trim().isEmpty() || !login.equals(login.trim()) || login.length() > 100
                    || login.chars().anyMatch(Character::isISOControl) || key(login).isEmpty())
                throw new IllegalArgumentException("登录列表账号文本为空、过长或含控制字符");
            if (uid == null || !uid.matches("[0-9]{9}")) throw new IllegalArgumentException("游戏 UID 须为9位数字");
            if (tacetIndex < 1 || tacetIndex > 21) throw new IllegalArgumentException("无音区编号须为1至21");
            this.login=login; this.uid=uid; this.tacetIndex=tacetIndex; this.enabled=enabled;
        }
        AccountDailyConfig.Profile profile() {
            return new AccountDailyConfig.Profile(uid, login, tacetIndex, Collections.emptyMap());
        }
        String signature() { return key(login) + "\t" + tacetIndex; }
    }
    final List<Account> accounts;
    MultiAccountPlan(List<Account> accounts) {
        if (accounts == null || accounts.size() > 20) throw new IllegalArgumentException("账号计划最多20项");
        HashSet<String> keys=new HashSet<>(), uids=new HashSet<>();
        for (Account a:accounts) if (a==null || !keys.add(key(a.login)) || !uids.add(a.uid))
            throw new IllegalArgumentException("账号文本或 UID 重复，请先核对绑定");
        this.accounts=Collections.unmodifiableList(new ArrayList<>(accounts));
    }
    // Match the config-store identity, not PC OCR's 0/o, case or .con fuzzy corrections.
    static String key(String text) { return text == null ? "" : text.replace("*", "").trim(); }
    static MultiAccountPlan parse(String text) {
        if (text==null) return new MultiAccountPlan(Collections.emptyList());
        if (text.length()>32768) throw new IllegalArgumentException("账号计划文件过大");
        String[] rows=text.split("\n",-1);
        if (!rows[0].equals("1")) throw new IllegalArgumentException("账号计划版本异常，保留文件");
        List<Account> accounts=new ArrayList<>();
        for (int i=1;i<rows.length;i++) {
            String[] fields=rows[i].split("\t",-1);
            if (fields.length!=4 || !fields[0].matches("[01]")) throw new IllegalArgumentException("账号计划行格式异常");
            accounts.add(new Account(fields[1],fields[2],Integer.parseInt(fields[3]),fields[0].equals("1")));
        }
        return new MultiAccountPlan(accounts);
    }
    String save() {
        StringBuilder out=new StringBuilder("1");
        for(Account a:accounts)out.append('\n').append(a.enabled?1:0).append('\t').append(a.login).append('\t').append(a.uid).append('\t').append(a.tacetIndex);
        return out.toString();
    }
    List<Account> enabled() {
        List<Account> out=new ArrayList<>(); for(Account a:accounts)if(a.enabled)out.add(a); return out;
    }
}
