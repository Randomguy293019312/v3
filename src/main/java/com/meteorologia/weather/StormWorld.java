package com.meteorologia.weather;

import java.util.ArrayList;
import java.util.List;

/** Storm state of one dimension (server side). */
public final class StormWorld {
    public final List<Supercell> supercells = new ArrayList<>();
    public final List<Tornado> tornadoes = new ArrayList<>();
    public int nextId = 1;
    /** 0 = no heavy rain, 0.35 light, 0.65 moderate, 1.0 heavy. */
    public float rainIntensity = 0F;
    public boolean sentEmpty = false;

    public Supercell findSupercell(int id) {
        for (Supercell s : supercells) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }
}
