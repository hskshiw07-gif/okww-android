package io.github.okww.weekly;

import java.util.Random;

/** Short post-observation pause; never randomize coordinates, combat timing or validation deadlines. */
final class GardenPacing {
    private final Random random;
    private int previous=-1;
    GardenPacing(Random random){this.random=random;}
    int next(){
        int value=180+random.nextInt(141);
        if(value==previous)value=value==320?180:value+1;
        previous=value;return value;
    }
}
