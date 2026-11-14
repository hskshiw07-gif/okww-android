package io.github.okww.weekly;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only first-party CharFactory data, never workshop/profile class loaders. */
final class BuiltinCombatCatalog {
    enum Role { MAIN_DPS, SUB_DPS, HEALER }
    static final class Character {
        final String name;
        final Role role;
        final int element;
        final long buffMs;
        final List<String> portraits = new ArrayList<>();
        Character(String name, Role role, int element, long buffMs) {
            this.name = name; this.role = role; this.element = element; this.buffMs = buffMs;
        }
    }
    final Map<String, Character> characters;
    final Map<String, String> hashes;

    BuiltinCombatCatalog(Reader source) throws IOException {
        Map<String, Character> result = new LinkedHashMap<>();
        Map<String, String> checksums = new LinkedHashMap<>();
        String line;
        BufferedReader input = new BufferedReader(source);
        while ((line = input.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length != 6 || !fields[0].matches("[A-Za-z][A-Za-z0-9]*")
                    || !fields[4].matches("[A-Za-z0-9_]+_[0-9]+x[0-9]+_[0-9]+_[0-9]+\\.png")
                    || !fields[5].matches("[0-9a-f]{64}")) throw new IllegalArgumentException("内置战斗目录格式异常");
            Role role = Role.valueOf(fields[1]);
            int element = Integer.parseInt(fields[2]); long buff = Long.parseLong(fields[3]);
            if (element < -1 || element > 5 || buff < 0 || buff > 120000) throw new IllegalArgumentException("角色属性越界");
            Character character = result.get(fields[0]);
            if (character == null) {
                character = new Character(fields[0], role, element, buff); result.put(fields[0], character);
            } else if (character.role != role || character.element != element || character.buffMs != buff)
                throw new IllegalArgumentException("同名角色配置冲突");
            if (checksums.put(fields[4], fields[5]) != null) throw new IllegalArgumentException("头像模板重复");
            character.portraits.add(fields[4]);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("内置战斗目录为空");
        characters = Collections.unmodifiableMap(result); hashes = Collections.unmodifiableMap(checksums);
    }

    Character require(String name) {
        Character character = characters.get(name);
        if (character == null) throw new IllegalStateException("未注册的内置角色：" + name);
        return character;
    }
}
