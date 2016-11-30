package myteam;

import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarTurretBrain;
import edu.warbot.communications.WarMessage;

import java.util.Stack;

public abstract class WarTurretBrainController extends WarTurretBrain
{
	
	private Stack<WTask> aStack; // Pile des activités à effectuer
	private WTask ctask; // Une activité
	

	public WarTurretBrainController() {
		super();
		ctask = stayIdleTask;
		aStack = new Stack<WTask>();
		aStack.push(ctask);
	}

	public String action() {
		
		// Traitement des messages
		handlingMessages();

		// Exécution des reflexes s'il y en a
		String reflex = doReflexes();
		if (reflex != null)
			return reflex;

		// Sinon exécution de l'activité courrante
		return ctask.exec(this);
	}
	
	
	/*******************************************************
	 ******************** MESSAGE HANDLING *****************
	 *******************************************************/
	public void handlingMessages() {
		
		// Traitement des messages reçus
		for (WarMessage msg : getMessages()) {
			
		}
		
		// Message à envoyer selon la perception
		for (WarAgentPercept p : getPercepts()) {
			
		}
		
		// Message à envoyer selon l'état
		if (isBagEmpty())
			broadcastMessageToAgentType(WarAgentType.WarExplorer, "turretNeedFood", Integer.toString(getID()));
	}
	

	/*******************************************************
	 ******************** REFLEXES *************************
	 *******************************************************/
	public String doReflexes() {
		return null;
	}
	
	
	/*******************************************************
	 ********************* ACTIVITES ***********************
	 *******************************************************/
	
	static WTask stayIdleTask = new WTask() {
		
		@Override
		String exec(WarBrain bc) {
			WarTurretBrainController me = (WarTurretBrainController) bc;
			
			me.setDebugString("Idle");

			if (me.enemySpotted()){
				me.ctask = attackTask;
			}
			else if (me.isHealthCritic()){
				me.ctask = healMySelfTask;
			}
			else{
				double heading = (me.getHeading() + 90) % 360;
				me.setHeading(heading);
			}

			return me.idle();
		}
	};
	
	static WTask attackTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarTurretBrainController me = (WarTurretBrainController) bc;
			
			me.setDebugString("Attack");

			if(!me.enemySpotted()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			}
			else{
				if(me.isReloaded()) {
	                return me.fire();
	            } else
	                return me.beginReloadWeapon();
			}
		}
	};
	
	static WTask healMySelfTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarTurretBrainController me = (WarTurretBrainController) bc;
			
			me.setDebugString("Heal me");

			if (me.isHealthGood()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.isBagEmpty())
				return me.idle();

			return me.eat();
		}
	};
	
	
	/*******************************************************
	 *********** CONDITIONS CHANGEMENT ACTIVITE ************
	 *******************************************************/
	
	public boolean isHealthCritic() {
		return getHealth() < 0.5 * getMaxHealth();
	}

	public boolean isHealthGood() {
		return getHealth() > 0.8 * getMaxHealth();
	}
	
	public boolean enemySpotted(){
		for (WarAgentPercept p : getPercepts()) {
        	if(isEnemy(p)) {
	            setHeading(p.getAngle());
	            return true;
	        }
        }
		return false;
	}

}
