package fr.lkdm.homelink.energy.wind;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only diagnostic. Holds the specified wind for one target interval, then resumes nature. */
public final class WindCommands {
    private WindCommands() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("homelink_wind").requires(source->source.hasPermission(2))
                .then(Commands.argument("strength",DoubleArgumentType.doubleArg(0,1)).executes(context->{
                    var data=WindSavedData.get(context.getSource().getLevel());
                    double strength=DoubleArgumentType.getDouble(context,"strength");
                    data.state().setStrength(strength,data.parameters().targetInterval()); data.setDirty();
                    context.getSource().sendSuccess(()->Component.literal("HomeLink wind: "+Math.round(strength*100)+"% ("+data.parameters().targetInterval()+" ticks)"),true);
                    return 1;
                })));
    }
}
