package myteam;

import edu.warbot.agents.agents.WarExplorer;
import edu.warbot.agents.agents.WarHeavy;
import edu.warbot.agents.agents.WarLight;
import edu.warbot.agents.agents.WarRocketLauncher;
import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.agents.projectiles.WarBullet;
import edu.warbot.agents.projectiles.WarRocket;
import edu.warbot.agents.resources.WarFood;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarLightBrain;
import edu.warbot.communications.WarMessage;

import java.util.List;
import java.util.Stack;

public abstract class WarLightBrainController extends WarLightBrain {

	private Stack<WTask> aStack; // Pile des activites a† effectuer
	private WTask ctask; // Une activite

	private List<WarAgentPercept> percepts;
	private List<WarMessage> messages;

	// Enemy Base attack attributes
	boolean baseAttacked = false;
	boolean baseIsSafe = true;
	
	double distanceToEBase = 0;
	double angleToEBase = 0;
	
	// Base defence attributes
	boolean enemyBaseSpotted = false;
	boolean endOfAttack = true;

	double distanceToBase = 0;
	double angleToBase = 0;
	
	// Keeping distance beetween the agents
	int wigglingSince = 0;
	static final int timeToWiggle = 50;

	public WarLightBrainController() {
		super();
		ctask = wiggleTask;
		aStack = new Stack<WTask>();

	}

	@Override
	public String action() {

		requestRole("Soldiers", "Light");
		
		messages = getMessages();
		percepts = getPercepts();

		// Traitement des messages
		handlingMessages();

		// Ex√©cution des reflexes s'il y en a
		String reflex = doReflexes();
		if (reflex != null)
			return reflex;

		// Sinon execution de l'activite courrante
		return ctask.exec(this);
	}

	/*******************************************************
	 ******************** MESSAGE HANDLING *****************
	 *******************************************************/
	public void handlingMessages() {

		for (WarMessage msg : messages) {

			// Si la base ennemie est reperee
			if (msg.getMessage().equals("enemyBaseSpotted")) {

				Vector2 exToEBase = new Vector2(Float.valueOf(msg.getContent()[0]), Float.valueOf(msg.getContent()[1]));
				Vector2 rLauncherToEx = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
				Vector2 rLauncherToEBase = rLauncherToEx.add(exToEBase);
				angleToEBase = VUtils.polaireFromCart(rLauncherToEBase).x;
				distanceToEBase = VUtils.polaireFromCart(rLauncherToEBase).y;

				if (!enemyBaseSpotted) {
					enemyBaseSpotted = true;
					endOfAttack = false;

					aStack.push(ctask);
					ctask = attackTask;
				}
			} else if (msg.getMessage().equals("baseAttacked")) {
				baseAttacked = true;
				baseIsSafe = false;
				angleToBase = msg.getAngle();
				distanceToBase = msg.getDistance();
				aStack.push(ctask);
				ctask = defendTask;
			}

		}
	}

	/*******************************************************
	 ******************** REFLEXES *************************
	 *******************************************************/
	public String doReflexes() {

		if (isBlocked()) {
			setRandomHeading();
			return ACTION_MOVE;
		}

		for (WarAgentPercept percept : percepts) {
			if (percept.getType() != WarAgentType.WarFood && percept.getDistance() <= WarFood.MAX_DISTANCE_TAKE) {
				return ACTION_TAKE;
			} else if (isEnemy(percept) && percept.getType() != WarAgentType.WarFood) {
				setHeading(percept.getAngle());
				if (isReloaded()) {
					return ACTION_FIRE;
				} else
					return ACTION_RELOAD;
			} else if (!isEnemy(percept) && percept.getType() == WarAgentType.WarBase) {
				setRandomHeading();
				return ACTION_MOVE;
			}
		}

		return null;
	}

	/*******************************************************
	 ********************* ACTIVITES ***********************
	 *******************************************************/

	static WTask wiggleTask = new WTask() {

		@Override
		String exec(WarBrain bc) {

			WarLightBrainController me = (WarLightBrainController) bc;

			me.setDebugString("Wiggle");

			if (me.tooCloseFromFriend()) {
				me.wigglingSince = 0;
				me.setRandomHeading();
			} else if (!me.tooCloseFromFriend() && me.wigglingSince < WarLightBrainController.timeToWiggle) {
				me.wigglingSince++;
			} else if (!me.tooCloseFromFriend() && !me.aStack.isEmpty()) {
				me.ctask = me.aStack.pop();
			}

			return me.move();
		}
	};

	/**************************************
	 ******* ATTACK THE ENEMY BASE ********
	 **************************************/
	static WTask attackTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarLightBrainController me = (WarLightBrainController) bc;

			me.setDebugString("Attack");

			if (me.endOfAttack) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.tooCloseFromFriend()) {
				me.aStack.push(me.ctask);
				me.ctask = wiggleTask;
				return ACTION_IDLE;
			}

			// Si la base enemie est a portee
			if (me.distanceToEBase < WarBullet.RANGE) {
				me.setHeading(me.angleToEBase);
				if (!me.isReloaded())
					return me.beginReloadWeapon();
			} else {
				me.setHeading(me.angleToEBase);
				return me.move();
			}

			return me.fire();
		}
	};

	/**************************************
	 *********** DEFEND THE BASE **********
	 **************************************/
	static WTask defendTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarLightBrainController me = (WarLightBrainController) bc;

			me.setDebugString("Defend");

			if (!me.baseAttacked) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.tooCloseFromFriend()) {
				me.aStack.push(me.ctask);
				me.ctask = wiggleTask;
				return ACTION_IDLE;
			}

			for (WarAgentPercept percept : me.percepts) {
				if (me.isEnemySoldier(percept)) {
					if (me.isReloaded()) {
						me.setHeading(percept.getAngle());
						return ACTION_FIRE;
					} else
						return ACTION_RELOAD;
				} else if (percept.getType() == WarAgentType.WarBase && !me.isEnemy(percept)) {
					return me.idle();
				}
			}

			me.setHeading(me.angleToBase);

			return ACTION_MOVE;
		}
	};

	/*******************************************************
	 *********** CONDITIONS CHANGEMENT ACTIVITE ************
	 *******************************************************/

	/*
	 * @param Un percept de l'agent
	 * 
	 * @return Vrai si l'agent est un type combattant et appartient a l'ennemi
	 */
	public boolean isEnemySoldier(WarAgentPercept percept) {
		return isEnemy(percept) && (percept.getType() == WarAgentType.WarRocketLauncher
				|| percept.getType() == WarAgentType.WarHeavy || percept.getType() == WarAgentType.WarLight);
	}

	public boolean tooCloseFromFriend() {

		for (WarAgentPercept percept : percepts)
			if (!isEnemy(percept) && (percept.getType().equals(WarAgentType.WarRocketLauncher)
					|| percept.getType().equals(WarAgentType.WarHeavy)
					|| percept.getType().equals(WarAgentType.WarLight))) {
				
				return true;
			}

		return false;

	}

}
