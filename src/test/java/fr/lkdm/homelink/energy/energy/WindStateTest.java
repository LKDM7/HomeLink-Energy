package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homelink.energy.wind.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindStateTest {
    @Test void smoothBoundedAndDeterministicAcrossSave() {
        var data = new WindSavedData(42);
        for (int i=0;i<10000;i++) {
            double before=data.state().currentStrength(); data.tick();
            assertTrue(Math.abs(before-data.state().currentStrength()) <= WindParameters.DEFAULT.step()+1e-12);
            assertTrue(data.state().currentStrength() >= .1 && data.state().currentStrength() <= 1);
        }
        var restored=WindSavedData.load(data.save(new CompoundTag(),null));
        for(int i=0;i<10000;i++) {
            data.tick(); restored.tick();
            assertEquals(data.state().currentStrength(), restored.state().currentStrength());
            assertEquals(data.state().trend(),restored.state().trend());
            assertEquals(data.state().nextTargetChange(),restored.state().nextTargetChange());
        }
    }
    @Test void malformedValuesAreClamped() {
        var state=new WindState(0); state.restore(Double.NaN,2,-1,-9,0);
        assertEquals(.55,state.currentStrength()); assertEquals(1,state.targetStrength()); assertEquals(0,state.ticks());
    }
}
