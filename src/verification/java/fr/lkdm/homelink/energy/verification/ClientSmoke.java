package fr.lkdm.homelink.energy.verification;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.client.BatteryScreen;
import fr.lkdm.homelink.energy.client.SolarPanelScreen;
import fr.lkdm.homelink.energy.menu.BatteryMenu;
import fr.lkdm.homelink.energy.menu.SolarPanelMenu;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Automated real client/server menus, translations and model checks. Never shipped. */
@EventBusSubscriber(modid=EnergyValidation.MOD_ID, value=Dist.CLIENT)
public final class ClientSmoke {
    private static int stage, age;
    private static long start;
    private static volatile Throwable failure;
    private static volatile BlockPos batteryPos;
    private static volatile BlockPos windOrigin;
    private static int visualView;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("energy.smoke") || stage==9) return;
        var mc=Minecraft.getInstance();
        try {
            if (stage==0) {
                if (mc.screen instanceof AccessibilityOnboardingScreen screen) { screen.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                checkModels(mc);
                stage=1; start=System.nanoTime();
                var rules=new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                var settings=new LevelSettings("Energy verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("energy-smoke-"+System.currentTimeMillis(),settings,new WorldOptions(0,false,false),
                    registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
                return;
            }
            if (failure!=null) throw new IllegalStateException("Server fixture failed",failure);
            if (System.nanoTime()-start>180_000_000_000L) throw new IllegalStateException("Client timeout stage="+stage);
            if (stage==1 && mc.player!=null && mc.getSingleplayerServer()!=null) {
                stage=10; age=0;
                var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(id); var level=p.serverLevel();
                        level.setDayTime(6000); level.setWeatherParameters(1000000,0,false,false);
                        var pos=p.blockPosition().offset(2,0,0); batteryPos=pos;
                        level.setBlockAndUpdate(pos,EnergyRegistries.BATTERY_1.get().defaultBlockState());
                        level.setBlockAndUpdate(pos.above(),EnergyRegistries.SOLAR_PANEL_3.get().defaultBlockState());
                        EnergyRegistries.SOLAR_PANEL_3.get().setPlacedBy(level, pos.above(), level.getBlockState(pos.above()), p, net.minecraft.world.item.ItemStack.EMPTY);
                        var battery=(BatteryBlockEntity)level.getBlockEntity(pos);
                        var tag=battery.saveWithFullMetadata(level.registryAccess()); tag.putLong("energy",39000);
                        battery.loadWithComponents(tag,level.registryAccess());
                        gallery(level,p,pos.offset(0,0,6));
                    } catch(Throwable t) { failure=t; }
                });
            } else if(stage==10 && mc.player!=null && batteryPos!=null) {
                mc.options.hideGui=true;
                mc.options.fov().set(50);
                if(++age<100) return;
                capture(mc,"models"); stage=11; age=0;
                var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(id);
                        p.teleportTo(p.serverLevel(),batteryPos.getX()+3.5,batteryPos.getY()+2.2,batteryPos.getZ()+16,180,24);
                    } catch(Throwable t) { failure=t; }
                });
            } else if(stage==11 && mc.player!=null) {
                if(++age<40) return;
                capture(mc,"battery-tiers"); mc.options.hideGui=false; mc.options.fov().set(70); stage=2; age=0;
                var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(id); var level=p.serverLevel();
                        p.teleportTo(batteryPos.getX()-2,batteryPos.getY(),batteryPos.getZ());
                        level.getBlockState(batteryPos.above()).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(batteryPos.above()),Direction.UP,batteryPos.above(),false));
                    } catch(Throwable t) { failure=t; }
                });
            } else if(stage==2 && mc.screen instanceof SolarPanelScreen && mc.player.containerMenu instanceof SolarPanelMenu menu && menu.value(SolarPanelMenu.PER_CYCLE)==20000) {
                if (++age<30) return;
                if(menu.value(SolarPanelMenu.RATE_MILLI)<2600 || menu.value(SolarPanelMenu.SKY)!=1) throw new IllegalStateException("Invalid solar sync");
                capture(mc,"solar"); age=0; stage=3;
                var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(id); var level=p.serverLevel();
                        level.getBlockState(batteryPos).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(batteryPos),Direction.UP,batteryPos,false));
                    } catch(Throwable t) { failure=t; }
                });
            } else if(stage==3 && mc.screen instanceof BatteryScreen && mc.player.containerMenu instanceof BatteryMenu menu && menu.value(BatteryMenu.STORED)>39000) {
                if(++age<30) return;
                if(menu.value(BatteryMenu.CAPACITY)!=40000 || menu.value(BatteryMenu.STORED)>40000) throw new IllegalStateException("Invalid battery sync");
                capture(mc,"battery"); stage=30; age=0;
                var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(id);var level=p.serverLevel();var pos=batteryPos.offset(6,0,9);
                        var state=level.getBlockState(pos);var block=(fr.lkdm.homelink.energy.block.BatteryBlock)state.getBlock();
                        var clicked=block.positions(pos,state).getLast();
                        p.teleportTo(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+3,180,20);
                        level.getBlockState(clicked).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(clicked),Direction.SOUTH,clicked,false));
                    } catch(Throwable t) { failure=t; }
                });
            } else if(stage==30 && mc.screen instanceof BatteryScreen && mc.player.containerMenu instanceof BatteryMenu menu && menu.value(BatteryMenu.CAPACITY)==320000) {
                if(++age<30) return;
                if(menu.value(BatteryMenu.COMPLETE)!=1 || !menu.pos().equals(batteryPos.offset(6,0,9))) throw new IllegalStateException("Passive battery tile did not open controller");
                capture(mc,"battery-footprint-gui"); stage=4;
                var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(id);
                        p.teleportTo(batteryPos.getX()+40,batteryPos.getY(),batteryPos.getZ());
                    } catch(Throwable t) { failure=t; }
                });
            } else if(stage==4 && mc.player!=null && mc.player.containerMenu==mc.player.inventoryMenu) {
                stage=20; age=0;
                var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(id); var level=p.serverLevel(); windOrigin=batteryPos.offset(0,0,24);
                        fr.lkdm.homelink.energy.wind.WindSavedData.get(level).state().setStrength(.8,100000);
                        var blocks=java.util.List.of(EnergyRegistries.WIND_TURBINE_1.get(),EnergyRegistries.WIND_TURBINE_2.get(),EnergyRegistries.WIND_TURBINE_3.get());
                        for(int i=0;i<3;i++) {
                            var base=windOrigin.offset(i*9,0,0); var block=blocks.get(i);
                            var state=block.defaultBlockState().setValue(fr.lkdm.homelink.energy.block.WindTurbineBlock.FACING,Direction.SOUTH);
                            level.setBlockAndUpdate(base,state); block.setPlacedBy(level,base,state,null,net.minecraft.world.item.ItemStack.EMPTY);
                        }
                        p.getAbilities().flying=true; p.onUpdateAbilities();
                        p.teleportTo(level,windOrigin.getX()+9,windOrigin.getY()+8,windOrigin.getZ()+24,180,8);
                    } catch(Throwable t) { failure=t; }
                });
            } else if(stage==20 && mc.player!=null && windOrigin!=null) {
                mc.options.hideGui=true;
                if(++age<100) return;
                for(int i=0;i<3;i++) {
                    var turbine=(fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity)mc.level.getBlockEntity(windOrigin.offset(i*9,0,0));
                    if(turbine==null || turbine.targetRotorSpeed()<=0) throw new IllegalStateException("Missing rotor synchronization "+i);
                }
                capture(mc,"wind-models"); stage=21; age=0;
            } else if(stage==21) {
                if(++age<15) return;
                capture(mc,"wind-rotation"); stage=210; age=0; visualView=0; visualView(mc,visualView);
            } else if(stage==210) {
                if(++age<30) return;
                capture(mc,"material-view-"+visualView);
                if(++visualView<8) { age=0; visualView(mc,visualView); }
                else { mc.options.hideGui=false; stage=22; age=0; openWind(mc,false); }
            } else if(stage==22 && mc.screen instanceof fr.lkdm.homelink.energy.client.WindTurbineScreen && mc.player.containerMenu instanceof fr.lkdm.homelink.energy.menu.WindTurbineMenu menu) {
                if(++age<30) return;
                if(menu.value(fr.lkdm.homelink.energy.menu.WindTurbineMenu.NOMINAL)!=28000 || menu.value(fr.lkdm.homelink.energy.menu.WindTurbineMenu.ROTOR)!=1 || menu.value(fr.lkdm.homelink.energy.menu.WindTurbineMenu.RATE)<=0) throw new IllegalStateException("Invalid wind menu sync");
                capture(mc,"wind-gui"); click(mc,"gui.homelink_energy.rotor_area"); stage=23; age=0;
            } else if(stage==23 && mc.screen==null) {
                aimWind(mc);
                if(++age<20) return;
                capture(mc,"wind-area"); stage=24; age=0; openWind(mc,true);
            } else if(stage==24 && mc.screen instanceof fr.lkdm.homelink.energy.client.WindTurbineScreen && mc.player.containerMenu instanceof fr.lkdm.homelink.energy.menu.WindTurbineMenu menu) {
                if(++age<40) return;
                if(menu.value(fr.lkdm.homelink.energy.menu.WindTurbineMenu.OBSTRUCTION)!=1 || menu.value(fr.lkdm.homelink.energy.menu.WindTurbineMenu.RATE)!=0) throw new IllegalStateException("Obstructed wind menu mismatch");
                capture(mc,"wind-blocked"); click(mc,"gui.homelink_energy.locate_obstruction"); stage=25; age=0;
            } else if(stage==25 && mc.screen==null) {
                aimWind(mc);
                if(++age<40) return;
                capture(mc,"wind-obstacle");
                HomeLinkEnergy.LOGGER.info("ENERGY_SMOKE_OK language={} models=10 panelRate=true batteryCharge=true menuDistance=true windSpeed=true windMenu=true windOverlay=true obstruction=true",mc.options.languageCode);
                stop(mc);
            }
        } catch(Throwable t) { HomeLinkEnergy.LOGGER.error("ENERGY_SMOKE_FAILED",t); stop(mc); }
    }

    private static void checkModels(Minecraft mc) {
        for(String id:new String[]{"solar_panel_1","solar_panel_2","solar_panel_3","wind_turbine_1","wind_turbine_2","wind_turbine_3","battery_1","battery_2","battery_3","copper_energy_cable"}) {
            var model=mc.getModelManager().getModel(new ModelResourceLocation(HomeLinkEnergy.id(id),"inventory"));
            if(model==mc.getModelManager().getMissingModel() || model.getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY).contents().name().getPath().equals("missingno")) throw new IllegalStateException("Missing model/texture "+id);
            if(!I18n.exists("block.homelink_energy."+id)) throw new IllegalStateException("Missing translation "+id);
        }
        for(var id:java.util.List.of(fr.lkdm.homelink.energy.client.WindTurbineRenderer.TOWER,fr.lkdm.homelink.energy.client.WindTurbineRenderer.NACELLE,fr.lkdm.homelink.energy.client.WindTurbineRenderer.NACELLE_2,fr.lkdm.homelink.energy.client.WindTurbineRenderer.NACELLE_3,fr.lkdm.homelink.energy.client.WindTurbineRenderer.BLADE,fr.lkdm.homelink.energy.client.WindTurbineRenderer.HUB,fr.lkdm.homelink.energy.client.CableRenderer.TRACE,fr.lkdm.homelink.energy.client.CableRenderer.CORNER))
            if(mc.getModelManager().getModel(id)==mc.getModelManager().getMissingModel()) throw new IllegalStateException("Missing rotor model "+id);
    }
    private static void openWind(Minecraft mc,boolean block) {
        var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
        server.execute(()->{
            try {
                var p=server.getPlayerList().getPlayer(id); var level=p.serverLevel(); var pos=windOrigin.offset(18,0,0);
                var turbine=(fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity)level.getBlockEntity(pos);
                if(block) { level.setBlockAndUpdate(turbine.area().hub(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()); turbine.invalidateClearance(); }
                p.teleportTo(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+6,180,-55);
                var base=(fr.lkdm.homelink.energy.block.WindTurbineBlock)turbine.getBlockState().getBlock();
                var clicked=base.positions(pos,turbine.getBlockState()).getLast();
                level.getBlockState(clicked).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(clicked),Direction.SOUTH,clicked,false));
            } catch(Throwable t) { failure=t; }
        });
    }
    private static void visualView(Minecraft mc,int view) {
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        server.execute(()->{
            try {
                var p=server.getPlayerList().getPlayer(id);
                p.getAbilities().flying=true; p.onUpdateAbilities();
                if(view<6) {
                    int tier=view/2;var pos=windOrigin.offset(tier*9,0,0);int hub=new int[]{4,7,11}[tier];
                    double targetY=pos.getY()+(view%2==0?hub+.5:1),offset=view%2==0?5:4;
                    p.teleportTo(p.serverLevel(),pos.getX()+offset,targetY+1,pos.getZ()+offset,-225,20);
                } else {
                    var origin=batteryPos.offset(0,0,6);
                    p.teleportTo(p.serverLevel(),origin.getX()+(view==6?3:10),origin.getY()+2,origin.getZ()+7,180,18);
                }
            }catch(Throwable t){failure=t;}
        });
    }
    private static void click(Minecraft mc,String key) {
        var button=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button)
                .map(c->(net.minecraft.client.gui.components.Button)c).filter(b->b.getMessage().getString().equals(I18n.get(key))).findFirst().orElseThrow();
        if(!button.active) throw new IllegalStateException("Inactive wind button "+key);
        button.onPress();
    }
    private static void aimWind(Minecraft mc) { mc.player.setYRot(180); mc.player.setXRot(-55); }
    private static void gallery(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player,BlockPos origin) {
        var stone=net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
        for(int x=-1;x<=14;x++) for(int z=-1;z<=6;z++) level.setBlockAndUpdate(origin.offset(x,-1,z),stone);
        var panels=java.util.List.of(EnergyRegistries.SOLAR_PANEL_1.get(),EnergyRegistries.SOLAR_PANEL_2.get(),EnergyRegistries.SOLAR_PANEL_3.get());
        var batteries=java.util.List.of(EnergyRegistries.BATTERY_1.get(),EnergyRegistries.BATTERY_2.get(),EnergyRegistries.BATTERY_3.get());
        for(int i=0;i<3;i++) {
            var pos=origin.offset(i*3,0,0); var panel=panels.get(i);
            level.setBlockAndUpdate(pos,panel.defaultBlockState());panel.setPlacedBy(level,pos,level.getBlockState(pos),player,net.minecraft.world.item.ItemStack.EMPTY);
            var base=origin.offset(i*3,0,3); var battery=batteries.get(i);
            var state=battery.defaultBlockState().setValue(fr.lkdm.homelink.energy.block.BatteryBlock.FACING,Direction.SOUTH);
            level.setBlockAndUpdate(base,state); battery.setPlacedBy(level,base,state,player,net.minecraft.world.item.ItemStack.EMPTY);
        }
        var wire=EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState();
        for(int x=1;x<=11;x++) {
            var pos=origin.offset(x,0,4); level.setBlockAndUpdate(pos,wire);
        }
        for(int y=0;y<=3;y++) for(int z=2;z<=5;z++) level.setBlockAndUpdate(origin.offset(12,y,z),stone);
        for(int y=0;y<=3;y++) {
            var wall=wire.setValue(fr.lkdm.homelink.energy.block.CopperEnergyCableBlock.FACES.get(Direction.DOWN),y==0)
                    .setValue(fr.lkdm.homelink.energy.block.CopperEnergyCableBlock.FACES.get(Direction.EAST),true);
            if(y==3) wall=wall.setValue(fr.lkdm.homelink.energy.block.CopperEnergyCableBlock.FACES.get(Direction.UP),true);
            level.setBlockAndUpdate(origin.offset(11,y,4),wall);
        }
        for(int x=9;x<=12;x++) level.setBlockAndUpdate(origin.offset(x,4,4),stone);
        for(int x=9;x<11;x++) level.setBlockAndUpdate(origin.offset(x,3,4),wire
                .setValue(fr.lkdm.homelink.energy.block.CopperEnergyCableBlock.FACES.get(Direction.DOWN),false)
                .setValue(fr.lkdm.homelink.energy.block.CopperEnergyCableBlock.FACES.get(Direction.UP),true));
        player.getAbilities().flying=true;player.onUpdateAbilities();
        player.teleportTo(level,origin.getX()-3.5,origin.getY()+5.5,origin.getZ()+12.5,-138,28);
    }
    private static void capture(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,"energy-"+name+"-"+mc.options.languageCode+".png",mc.getMainRenderTarget(),message->HomeLinkEnergy.LOGGER.info("{}",message.getString()));
    }
    private static void stop(Minecraft mc) {
        stage=9;
        if(mc.level!=null) mc.level.disconnect();
        mc.disconnect(); mc.stop();
    }
}
