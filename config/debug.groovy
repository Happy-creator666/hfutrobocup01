import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;
import hfutinvincible.debug.Debugger;

def agentId = new EntityID(319226127)
def agentBrain = agentBrainMap.get(agentId)
def ai = agentBrain.agentInfo
def wi = agentBrain.worldInfo
def si = agentBrain.scenarioInfo
def painter = Debugger.getPainterOfAgent(ai)
Debugger.removeNonPersistPaintables(ai)
Debugger.registerOrRefreshAgent(wi, ai, si)