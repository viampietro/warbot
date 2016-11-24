package myteam;


import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarBaseBrain;
import edu.warbot.communications.WarMessage;

import java.util.List;

public abstract class WarBaseBrainController extends WarBaseBrain {

	private WTask ctask;
	int createdSoldiers = 0;

	/***********************************
	 ************** IDLE ***************
	 ***********************************/
	static WTask stayIdleTask = new WTask() {
		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;

			if (me.isEnemyBaseSpotted())
				me.ctask = createSoldierTask;

			return me.idle();
		}
	};

	/*********************************************
	 ************** CREATE SOLDIER ***************
	 *********************************************/
	static WTask createSoldierTask = new WTask() {

		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;
			
			System.out.println("Health : " + me.getHealth() + "/" + me.getMaxHealth());
			if (me.attackTerminated()) {
				me.createdSoldiers = 0;
				me.ctask = stayIdleTask;
				return me.idle();
			} else {
				if(me.isAbleToCreate(WarAgentType.WarRocketLauncher))
					
				if (me.createdSoldiers < 2) {
					System.out.println("Je crée un WarRocketLauncher");
					me.setNextAgentToCreate(WarAgentType.WarEngineer);
					me.createdSoldiers++;
				} else {
					if (me.getHealth() == me.getMaxHealth())
						me.createdSoldiers = 0;
					else if (!me.isBagEmpty()) {
						System.out.println("Je mange");
						return me.eat();
					} else {
						System.out.println("Je demande de la nourriture");
						me.broadcastMessageToAgentType(WarAgentType.WarExplorer, "bring food to base", "");
						return me.idle();
					}
				}
			}
			
			return me.create();
		}
	};

	public WarBaseBrainController() {
		super();
		ctask = stayIdleTask;
	}

	@Override
	public String action() {

		setDebugString("Base");

		List<WarMessage> msgs = getMessages();
		for (WarMessage msg : msgs) {
			if (msg.getMessage().equals("Give me your ID base")) {
				setDebugString("I'm here");
				reply(msg, "I am the base and here is my ID", Integer.toString(getID()));
			}
		}

		return ctask.exec(this);
	}

	/*******************************************************
	 ************ ACTIVTY CHANGING CONDITIONS **************
	 *******************************************************/

	public boolean isEnemyBaseSpotted() {
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("Enemy base spotted")) {
				setDebugString("Explorers spotted the enemy base");
				return true;
			}
		}
		return false;
	}

	public boolean attackTerminated() {
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("Attack terminated")) {
				return true;
			}
		}
		return false;
	}
}
