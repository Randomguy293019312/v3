package com.meteorologia.weather;

import net.minecraft.network.FriendlyByteBuf;

/** Supercell: a large rotating storm that can spawn a tornado. */
public class Supercell {
    public int id;
    public double x, z;          // centre (blocks)
    public double vx, vz;        // velocity (blocks/tick)
    public float radius = 300F;  // updraft radius
    public float baseY = 180F;   // cloud base height (world Y)
    public float topY = 520F;    // top height
    public float strength;       // 0..1 (maturity)
    public float dStrength;      // change per tick (so the client can interpolate)
    public float windAngle;      // anvil direction (rad)

    // --- server only
    public int age;
    public int lifetime = 15000;
    public int maxEf = -1;       // tornado potential (-1 = none)
    public boolean hasTornado;

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeDouble(x);
        b.writeDouble(z);
        b.writeFloat((float) vx);
        b.writeFloat((float) vz);
        b.writeFloat(radius);
        b.writeFloat(baseY);
        b.writeFloat(topY);
        b.writeFloat(strength);
        b.writeFloat(dStrength);
        b.writeFloat(windAngle);
        b.writeByte(maxEf);
    }

    public static Supercell read(FriendlyByteBuf b) {
        Supercell s = new Supercell();
        s.id = b.readVarInt();
        s.x = b.readDouble();
        s.z = b.readDouble();
        s.vx = b.readFloat();
        s.vz = b.readFloat();
        s.radius = b.readFloat();
        s.baseY = b.readFloat();
        s.topY = b.readFloat();
        s.strength = b.readFloat();
        s.dStrength = b.readFloat();
        s.windAngle = b.readFloat();
        s.maxEf = b.readByte();
        return s;
    }
}
