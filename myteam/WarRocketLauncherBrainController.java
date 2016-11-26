package myteam;

import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.agents.projectiles.WarRocket;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarRocketLauncherBrain;
import edu.warbot.communications.WarMessage;

import java.util.List;
import java.util.Stack;

public abstract class WarRocketLauncherBrainController extends WarRocketLauncherBrain {

	WTask ctask;
	Stack<WTask> aStack;

	boolean baseAttacked = false;
	boolean enemyBaseSpotted = false;
	boolean baseIsSafe = true;
	boolean endOfAttack = true;

	double distanceToEBase = 0;
	double angleToEBase = 0;
	double distanceToBase = 0;
	double angleToBase = 0;

	/**************************************
	 ************* WIGGLE *****************
	 **************************************/
	static WTask wiggleTask = new WTask() {

		@Override
		String exec(WarBrain bc) {

			WarRocketLauncherBrainController me = (WarRocketLauncherBrainController) bc;

			if (me.enemyBaseSpotted) {
				me.aStack.push(me.ctask);
				me.ctask = attackTask;
			} else if (me.baseAttacked) {
				me.aStack.push(me.ctask);
				me.ctask = defendTask;
			}

			return ACTION_MOVE;
		}
	};

	/**************************************
	 ******* ATTACK THE ENEMY BASE ********
	 **************************************/
	static WTask attackTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarRocketLauncherBrainController me = (WarRocketLauncherBrainController) bc;

			if(me.endOfAttack) {
				me.ctask = me.aStack.pop();
				return ACTION_IDLE;
			}
			
			// Si la base enemie est a portee
			if (me.distanceToEBase <= WarRocket.RANGE) {
				if (me.isReloaded())
					me.setTargetDistance(me.distanceToEBase);
				else
					return ACTION_RELOAD;
			} else {
				me.setHeading(me.angleToEBase);
				return ACTION_MOVE;
			}

			return ACTION_FIRE;
		}
	};

	/**************************************
	 *********** DEFEND THE BASE **********
	 **************************************/
	static WTask defendTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarRocketLauncherBrainController me = (WarRocketLauncherBrainController) bc;

			if(!me.baseAttacked) {
				me.ctask = me.aStack.pop();
				return ACTION_IDLE;
			}
			
			for (WarAgentPercept percept : me.getPercepts()) {
				if (me.isEnemySoldier(percept) || percept.getAngle() == me.angleToBase) {
					if (me.isReloaded()) {
						me.setTargetDistance(percept.getDistance());
						return ACTION_FIRE;
					} else
						return ACTION_RELOAD;
				} else if (percept.getType() == WarAgentType.WarBase && !me.isEnemy(percept)) {
					return ACTION_IDLE;
				}
			}

			me.setHeading(me.angleToBase);

			return ACTION_MOVE;
		}
	};

	public WarRocketLauncherBrainController() {
		super();
		ctask = wiggleTask;
		aStack = new Stack<WTask>();
	}

	@Override
	public String action() {

		setDebugString("No target");

		// Traitement des messages
		handlingMessages();

		// Execution des reflexes
		String reflex = doReflexes();
		if (reflex != null)
			return reflex;

		if (isBlocked()) {
			setRandomHeading();
			return move();
		}

		return ctask.exec(this);
	}

	/*
	 * @param Un percept de l'agent
	 * 
	 * @return Vrai si l'agent est un type combattant et appartient a l'ennemi
	 */
	public boolean isEnemySoldier(WarAgentPercept percept) {
		return isEnemy(percept) && (percept.getType() == WarAgentType.WarRocketLauncher
				|| percept.getType() == WarAgentType.WarHeavy || percept.getType() == WarAgentType.WarLight);
	}

	/**************************************
	 *********** REFLEXES **********
	 **************************************/
	public String doReflexes() {
		for (WarAgentPercept percept : getPercepts()) {

			if (isEnemy(percept)) {
				if (isReloaded()) {
					setTargetDistance(percept.getDistance());
					return ACTION_FIRE;
				} else return ACTION_RELOAD;
			}
		}

		return null;
	}

	/**************************************
	 *********** MESSAGE HANDLING *********
	 **************************************/
	public void handlingMessages() {

		for (WarMessage msg : getMessages()) {

			// Si la base ennemie est reperee
			if (msg.getMessage().equals("Attack the enemy base")) {
				Vector2 exToEBase = new Vector2(Float.valueOf(msg.getContent()[0]), Float.valueOf(msg.getContent()[1]));
				Vector2 rLauncherToEx = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
				Vector2 rLauncherToEBase = rLauncherToEx.add(exToEBase);

				enemyBaseSpotted = true;
				endOfAttack = false;
				angleToEBase = VUtils.polaireFromCart(rLauncherToEBase).x;
				distanceToEBase = VUtils.polaireFromCart(rLauncherToEBase).y;

				setDebugString("On my way to enemy base");
			} else if (msg.getMessage().equals("Base is being attacked")) {
				baseAttacked = true;
				angleToBase = msg.getAngle();
				distanceToBase = msg.getDistance();
			}

		}
	}

}