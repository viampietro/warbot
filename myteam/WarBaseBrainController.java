package myteam;

import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarBaseBrain;
import edu.warbot.communications.WarMessage;

import java.util.List;
import java.util.Stack;

public abstract class WarBaseBrainController extends WarBaseBrain {

	// Cout de creation des unites
	private final static int LIGHT_COST = 250;
	private final static int HEAVY_COST = 500;
	private final static int EXPLORER_COST = 200;
	private final static int RLAUNCHER_COST = 1000;
	private final static int ENGINEER_COST = 1000;

	private Stack<WTask> aStack; // Pour activities stack
	private WTask ctask;

	/***********************************
	 ************** IDLE ***************
	 ***********************************/
	static WTask stayIdleTask = new WTask() {
		@Override
		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;

			if (me.isEnemyBaseSpotted())
				me.ctask = createSoldierTask;
			if (me.isHealthCritic())
				me.ctask = healMySelfTask;

			return me.idle();
		}
	};

	/*********************************************
	 ************** HEAL MYSELF ******************
	 *********************************************/
	static WTask healMySelfTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;

			if (me.isHealthGood()) {
				me.ctask = me.aStack.pop();
			} else if (me.getNbElementsInBag() == 0)
				return me.idle();

			return me.eat();
		}
	};

	/*********************************************
	 ************** CREATE SOLDIER ***************
	 *********************************************/
	static WTask createSoldierTask = new WTask() {

		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;

			if (me.isAttackTerminated()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.isHealthCritic()) {
				me.aStack.push(me.ctask);
				me.ctask = healMySelfTask;
				return me.idle();
			} else {

				if (me.getHealth() > RLAUNCHER_COST)
					me.setNextAgentToCreate(WarAgentType.WarRocketLauncher);
				else if (me.getHealth() > HEAVY_COST)
					me.setNextAgentToCreate(WarAgentType.WarHeavy);
				else if (me.getHealth() > LIGHT_COST)
					me.setNextAgentToCreate(WarAgentType.WarLight);
				else if (!me.isBagEmpty())
					return me.eat();
				else
					return me.idle();
			}

			return me.create();
		}
	};

	public WarBaseBrainController() {
		super();
		ctask = stayIdleTask;
		aStack = new Stack<WTask>();
		aStack.push(ctask);
	}

	@Override
	public String action() {

		// Traitement des messages
		handlingMessages();

		// Execution des reflexes
		String reflex = doReflexes();
		if (reflex != null)
			return reflex;

		return ctask.exec(this);
	}

	/*******************************************************
	 ************ ACTIVITY CHANGING CONDITIONS **************
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

	public boolean isAttackTerminated() {
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("Attack terminated")) {
				return true;
			}
		}
		return false;
	}

	public boolean isHealthCritic() {
		return getHealth() < 0.5 * getMaxHealth();
	}

	public boolean isHealthGood() {
		return getHealth() >= 0.8 * getMaxHealth();
	}

	/*******************************************************
	 ******************** MESSAGE HANDLING *****************
	 *******************************************************/
	public void handlingMessages() {
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("Give me your ID base")) {
				setDebugString("I'm here");
				reply(msg, "I am the base and here is my ID", Integer.toString(getID()));
			}
		}

		if (getNbElementsInBag() == 0)
			broadcastMessageToAgentType(WarAgentType.WarExplorer, "Bring food to base", "");
	}

	/*******************************************************
	 ******************** REFLEXES *************************
	 *******************************************************/
	public String doReflexes() {

		if (getHealth() < getMaxHealth() && !isBagEmpty())
			return ACTION_EAT;
		
		return null;
	}
}
