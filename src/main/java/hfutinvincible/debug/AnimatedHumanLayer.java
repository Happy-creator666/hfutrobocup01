package hfutinvincible.debug;

import rescuecore2.config.Config;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.misc.AgentPath;
import rescuecore2.standard.view.HumanLayer;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * 官方实现的 AnimatedHumanLayer 有问题，不用他们的
 *
 * @author stardust
 */
public class AnimatedHumanLayer extends HumanLayer {
    private Set<EntityID> humanIDs = new HashSet();
    private Map<EntityID, Queue<Pair<Integer, Integer>>> frames = new HashMap();
    private boolean animationDone = true;

    public AnimatedHumanLayer() {
    }

    public void initialise(Config config) {
        super.initialise(config);
        this.humanIDs.clear();
        synchronized(this) {
            this.frames.clear();
            this.animationDone = true;
        }
    }

    public String getName() {
        return "Humans (animated)";
    }

    public boolean nextFrame() {
        synchronized(this) {
            if (this.animationDone) {
                return false;
            } else {
                this.animationDone = true;
                Iterator var2 = this.frames.values().iterator();

                while(var2.hasNext()) {
                    Queue<Pair<Integer, Integer>> next = (Queue)var2.next();
                    if (next.size() > 0) {
                        next.remove();
                        this.animationDone = false;
                    }
                }

                return !this.animationDone;
            }
        }
    }

    protected Pair<Integer, Integer> getLocation(Human h) {
        synchronized(this) {
            Queue<Pair<Integer, Integer>> agentFrames = (Queue)this.frames.get(h.getID());
            if (agentFrames != null && !agentFrames.isEmpty()) {
                return (Pair)agentFrames.peek();
            }
        }

        return h.getLocation(this.world);
    }

    protected void preView() {
        super.preView();
        this.humanIDs.clear();
    }

    protected void viewObject(Object o) {
        super.viewObject(o);
        if (o instanceof Human) {
            this.humanIDs.add(((Human)o).getID());
        }
    }

    void computeAnimation(int frameCount) {
        synchronized(this) {
            this.frames.clear();
            double step = 1.0D / ((double)frameCount - 1.0D);
            Iterator var5 = this.humanIDs.iterator();

            while(true) {
                EntityID next;
                LinkedList result;
                AgentPath path;
                do {
                    Human human;
                    do {
                        if (!var5.hasNext()) {
                            this.animationDone = false;
                            return;
                        }

                        next = (EntityID)var5.next();
                        result = new LinkedList();
                        human = (Human)this.world.getEntity(next);
                    } while(human == null);

                    StandardEntity position = (StandardEntity)this.world.getEntity(human.getPosition());
                    if (position instanceof AmbulanceTeam) {
                        path = AgentPath.computePath((AmbulanceTeam)position, this.world);
                    } else {
                        path = AgentPath.computePath(human, this.world);
                    }
                } while(path == null);

                for(int i = 0; i < frameCount; ++i) {
                    Pair<Integer, Integer> nextPoint = path.getPointOnPath((double)i * step);
                    result.add(nextPoint);
                }

                this.frames.put(next, result);
            }
        }
    }
}