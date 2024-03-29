package hfutinvincible.tool.debugtest;

import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.targetprovider.TargetProvider;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

@HfutTool
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class FileChangeTest implements LifeCycle {

    @Autowired
    TargetProvider<Area> targetProvider;

    @Autowired
    Logger logger;

    @Override
    public void resume(PrecomputeData precomputeData) {
        registerListener();
    }

    @Override
    public void preparate() {
        registerListener();
    }

    private void registerListener() {
        System.out.println("register listener");
        Params params = new Params(this.getClass().getSimpleName());
        params.registerParamsChangedListener(() -> {
            System.out.println("Configuration file has been changed!");
        });
    }
}