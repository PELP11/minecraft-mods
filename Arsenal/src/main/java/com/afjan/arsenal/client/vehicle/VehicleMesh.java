package com.afjan.arsenal.client.vehicle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.InflaterInputStream;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.Arsenal;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * A vehicle's mesh as written by tools/vehicle_models.py: named parts made of textured quads, each with the pivot and
 * axis it moves about. Loaded once from {@code assets/<ns>/vehicle/<name>.mesh} (zlib-compressed, little endian).
 */
public final class VehicleMesh {
    /** Floats per quad: 4 corners of (x y z u v), then the face normal. */
    public static final int QUAD_FLOATS = 23;

    public record Part(String name, @Nullable String parent, boolean translucent, boolean emissive, Vector3f pivot,
            Vector3f axis, float range, float[] data, boolean[] glow) {
        public int quads() {
            return this.glow.length;
        }
    }

    private static final Map<Identifier, VehicleMesh> CACHE = new HashMap<>();

    public final List<Part> parts;
    public final Map<String, Part> byName;

    private VehicleMesh(List<Part> parts) {
        this.parts = parts;
        this.byName = new HashMap<>();
        for (Part part : parts) {
            this.byName.put(part.name(), part);
        }
    }

    /** The mesh, loaded on first use (null if it cannot be read: the renderer then draws nothing). */
    public static @Nullable VehicleMesh get(Identifier id) {
        if (CACHE.containsKey(id)) {
            return CACHE.get(id);
        }
        VehicleMesh mesh = null;
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = new InflaterInputStream(resource.get().open())) {
                    mesh = read(in.readAllBytes());
                }
            } else {
                Arsenal.LOGGER.error("Missing vehicle mesh {}", id);
            }
        } catch (IOException | RuntimeException e) {
            Arsenal.LOGGER.error("Could not read vehicle mesh {}", id, e);
        }
        CACHE.put(id, mesh);
        return mesh;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static VehicleMesh read(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (!"ARSM".equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("not a vehicle mesh");
        }
        buf.getInt(); // version
        buf.getInt(); // atlas width
        buf.getInt(); // atlas height
        int count = buf.getInt();
        List<Part> parts = new ArrayList<>(count);
        for (int p = 0; p < count; p++) {
            String name = string(buf);
            String parent = string(buf);
            int flags = buf.get() & 0xFF;
            Vector3f pivot = new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat());
            Vector3f axis = new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat());
            float range = buf.getFloat();
            int quads = buf.getInt();
            float[] data = new float[quads * QUAD_FLOATS];
            boolean[] glow = new boolean[quads];
            for (int q = 0; q < quads; q++) {
                glow[q] = buf.get() != 0;
                for (int i = 0; i < QUAD_FLOATS; i++) {
                    data[q * QUAD_FLOATS + i] = buf.getFloat();
                }
            }
            parts.add(new Part(name, parent.isEmpty() ? null : parent, (flags & 1) != 0, (flags & 2) != 0, pivot, axis,
                    range, data, glow));
        }
        return new VehicleMesh(parts);
    }

    private static String string(ByteBuffer buf) {
        int length = buf.getShort() & 0xFFFF;
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
