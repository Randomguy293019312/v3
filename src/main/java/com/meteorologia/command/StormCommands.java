package com.meteorologia.command;

import com.meteorologia.Meteorologia;
import com.meteorologia.weather.Compass;
import com.meteorologia.weather.StormManager;
import com.meteorologia.weather.StormWorld;
import com.meteorologia.weather.Supercell;
import com.meteorologia.weather.Tornado;
import com.meteorologia.weather.WindModel;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /storm tornado create [ef 0-5] [width 4-400] [auto|rope|cone|stovepipe|wedge]
 * /storm tornado clear
 * /storm supercell create [distance] | /storm supercell clear
 * /storm rain light|moderate|heavy|clear
 * /storm start | /storm stop
 * /storm wind | /storm info
 */
@EventBusSubscriber(modid = Meteorologia.MODID)
public final class StormCommands {
    private StormCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shapeLiteral(String name, int shape) {
        return Commands.literal(name).executes(ctx -> spawnTornado(ctx.getSource(),
                IntegerArgumentType.getInteger(ctx, "ef"), IntegerArgumentType.getInteger(ctx, "width"), shape));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("storm")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("tornado")
                        .then(Commands.literal("create")
                                .executes(ctx -> spawnTornado(ctx.getSource(), 2, 0, Tornado.SHAPE_AUTO))
                                .then(Commands.argument("ef", IntegerArgumentType.integer(0, 5))
                                        .executes(ctx -> spawnTornado(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "ef"), 0, Tornado.SHAPE_AUTO))
                                        .then(Commands.argument("width", IntegerArgumentType.integer(4, 400))
                                                .executes(ctx -> spawnTornado(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "ef"),
                                                        IntegerArgumentType.getInteger(ctx, "width"), Tornado.SHAPE_AUTO))
                                                .then(shapeLiteral("auto", Tornado.SHAPE_AUTO))
                                                .then(shapeLiteral("rope", Tornado.SHAPE_ROPE))
                                                .then(shapeLiteral("cone", Tornado.SHAPE_CONE))
                                                .then(shapeLiteral("stovepipe", Tornado.SHAPE_STOVEPIPE))
                                                .then(shapeLiteral("wedge", Tornado.SHAPE_WEDGE)))))
                        .then(Commands.literal("clear").executes(ctx -> clearTornadoes(ctx.getSource()))))
                .then(Commands.literal("supercell")
                        .then(Commands.literal("create")
                                .executes(ctx -> spawnSupercell(ctx.getSource(), 1500))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(200, 6000))
                                        .executes(ctx -> spawnSupercell(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "distance")))))
                        .then(Commands.literal("clear").executes(ctx -> clearAll(ctx.getSource()))))
                .then(Commands.literal("rain")
                        .then(Commands.literal("light").executes(ctx -> rain(ctx.getSource(), 0.35F, false, "Light rain")))
                        .then(Commands.literal("moderate").executes(ctx -> rain(ctx.getSource(), 0.65F, false, "Moderate rain")))
                        .then(Commands.literal("heavy").executes(ctx -> rain(ctx.getSource(), 1.0F, true, "Heavy rain")))
                        .then(Commands.literal("clear").executes(ctx -> rain(ctx.getSource(), 0F, false, "Clear weather"))))
                .then(Commands.literal("start").executes(ctx -> startStorm(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> stopStorm(ctx.getSource())))
                .then(Commands.literal("wind").executes(ctx -> wind(ctx.getSource())))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource()))));
    }

    private static void say(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
    }

    private static String shapeName(int shape) {
        switch (shape) {
            case Tornado.SHAPE_ROPE: return "rope";
            case Tornado.SHAPE_CONE: return "cone";
            case Tornado.SHAPE_STOVEPIPE: return "stovepipe";
            case Tornado.SHAPE_WEDGE: return "wedge";
            default: return "auto";
        }
    }

    private static int spawnTornado(CommandSourceStack src, int ef, int width, int shape) {
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        Vec3 look = Vec3.directionFromRotation(src.getRotation());
        // far enough to fit the tornado on screen, but always inside the loaded chunks
        // (a tornado spawned beyond them cannot know the terrain height and would hover)
        int loaded = level.getServer().getPlayerList().getViewDistance() * 16;
        double radius = width > 0 ? width / 2.0 : 40.0;
        double dist = Math.min(Math.max(80.0, radius * 1.5 + 60.0), Math.max(60.0, loaded - 48.0));
        double x = pos.x + look.x * dist;
        double z = pos.z + look.z * dist;
        Tornado t = StormManager.spawnTornado(level, x, z, ef, width, shape);
        say(src, "Created an EF" + t.ef + " " + shapeName(t.shape) + " tornado, "
                + (int) (t.radius * 2) + " blocks wide, " + (int) dist + " blocks away.");
        return 1;
    }

    private static int clearTornadoes(CommandSourceStack src) {
        StormWorld w = StormManager.get(src.getLevel());
        int n = w.tornadoes.size();
        w.tornadoes.clear();
        say(src, "Removed " + n + " tornado(es).");
        return n;
    }

    private static int spawnSupercell(CommandSourceStack src, int distance) {
        ServerLevel level = src.getLevel();
        StormWorld w = StormManager.get(level);
        Vec3 pos = src.getPosition();
        Supercell s = StormManager.spawnApproaching(level, w, pos.x, pos.z, distance, 3);
        say(src, "Supercell created " + Compass.distance(distance) + " away to the "
                + Compass.direction(s.x - pos.x, s.z - pos.z) + " (EF3 tornado potential).");
        return 1;
    }

    private static int clearAll(CommandSourceStack src) {
        StormManager.clearAll(src.getLevel());
        say(src, "All supercells and tornadoes removed.");
        return 1;
    }

    private static int rain(CommandSourceStack src, float intensity, boolean thunder, String name) {
        StormManager.setRain(src.getLevel(), intensity, thunder);
        say(src, name + ".");
        return 1;
    }

    private static int startStorm(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        StormManager.setRain(level, 1.0F, true);
        Vec3 pos = src.getPosition();
        StormManager.spawnApproaching(level, StormManager.get(level), pos.x, pos.z, 1800, 3);
        say(src, "Storm started: heavy rain and a supercell approaching.");
        return 1;
    }

    private static int stopStorm(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        StormManager.clearAll(level);
        StormManager.setRain(level, 0F, false);
        say(src, "Storm stopped.");
        return 1;
    }

    private static int wind(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Vec3 p = src.getPosition();
        double[] w = WindModel.wind(StormManager.get(level), level.getGameTime(), p.x, p.y, p.z);
        say(src, "Wind here: " + WindModel.describeWind(w));
        return 1;
    }

    private static int info(CommandSourceStack src) {
        Vec3 p = src.getPosition();
        for (String line : StormManager.report(src.getLevel(), p.x, p.z)) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
