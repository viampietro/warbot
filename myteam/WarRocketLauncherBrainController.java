package myteam;

import edu.warbot.agents.agents.WarHeavy;
import edu.warbot.agents.agents.WarRocketLauncher;
import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.agents.projectiles.WarRocket;
import edu.warbot.agents.resources.WarFood;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarRocketLauncherBrain;
import edu.warbot.communications.WarMessage;
import jogamp.common.os.elf.SectionHeader;

import java.util.List;
import java.util.Set;
import java.util.Stack;

public abstract class WarRocketLauncherBrainController extends WarRocketLauncherBrain {

	private Stack<WTask> aStack; // Pile des activites a† effectuer
	private WTask ctask; // Une activite

	private List<WarAgentPercept> percepts;
	private List<WarMessage> messages;

	boolean baseAttacked = false;
	boolean enemyBaseSpotted = false;
	boolean baseIsSafe = true;
	boolean endOfAttack = true;

	double distanceToEBase = 0;
	double angleToEBase = 0;
	double distanceToBase = 0;
	double angleToBase = 0;

	int wigglingSince = 0;

	static final int timeToWiggle = 50;

	public WarRocketLauncherBrainController() {
		super();
		ctask = wiggleTask;
		aStack = new Stack<WTask>();

	}

	@Override
	public String action() {

		requestRole("Soldiers", "RocketLauncher");
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
			} else if (msg.getMessage().equals("Base is being attacked")) {
				baseAttacked = true;
				angleToBase = msg.getAngle();
				distanceToBase = msg.getDistance();
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
					setTargetDistance(percept.getDistance());
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

			WarRocketLauncherBrainController me = (WarRocketLauncherBrainController) bc;

			me.setDebugString("Wiggle");

			if (me.tooCloseFromFriend()) {
				me.wigglingSince = 0;
				me.setRandomHeading(); 
			} else if (!me.tooCloseFromFriend() && me.wigglingSince < WarRocketLauncherBrainController.timeToWiggle) {
				me.wigglingSince++;
			} else if (!me.tooCloseFromFriend() && !me.aStack.isEmpty()) {
				me.ctask = me.aStack.pop();
			}

			return me.move();
		}};

	/**************************************
	 ******* ATTACK THE ENEMY BASE ********
	 **************************************/
	static WTask attackTask=new WTask(){

	@Override String exec(WarBrain bc){WarRocketLauncherBrainController me=(WarRocketLauncherBrainController)bc;

	me.setDebugString("Attack");

	if(me.endOfAttack){me.ctask=me.aStack.pop();return me.idle();}else if(me.tooCloseFromFriend()){me.aStack.push(me.ctask);me.ctask=wiggleTask;return ACTION_IDLE;}

	// Si la base enemie est a portee
	if(me.distanceToEBase<WarRocket.RANGE){me.setHeading(me.angleToEBase);if(me.isReloaded())me.setTargetDistance(me.distanceToEBase);else return me.beginReloadWeapon();}else{me.setHeading(me.angleToEBase);return me.move();}

	return me.fire();}};

	/**************************************
	 *********** DEFEND THE BASE **********
	 **************************************/
	static WTask defendTask=new WTask(){

	@Override String exec(WarBrain bc){WarRocketLauncherBrainController me=(WarRocketLauncherBrainController)bc;

	me.setDebugString("Defend");

	if(!me.baseAttacked){me.ctask=me.aStack.pop();return me.idle();}else if(me.tooCloseFromFriend()){me.aStack.push(me.ctask);me.ctask=wiggleTask;return ACTION_IDLE;}

	for(WarAgentPercept percept:me.percepts){if(me.isEnemySoldier(percept)||percept.getAngle()==me.angleToBase){if(me.isReloaded()){me.setHeading(percept.getAngle());me.setTargetDistance(percept.getDistance());return ACTION_FIRE;}else return ACTION_RELOAD;}else if(percept.getType()==WarAgentType.WarBase&&!me.isEnemy(percept)){return me.idle();}}

	me.setHeading(me.angleToBase);

	return ACTION_MOVE;}};

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
				System.out
						.println("Rocket Launcher " + getID() + " observes that is too close from " + percept.getID());
				return true;
			}

		return false;

	}
}