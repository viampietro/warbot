package myteam_1;

import edu.warbot.agents.agents.WarExplorer;
import edu.warbot.agents.agents.WarHeavy;
import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.agents.resources.WarFood;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarExplorerBrain;
import edu.warbot.communications.WarMessage;

import java.util.Stack;

public abstract class WarExplorerBrainController extends WarExplorerBrain {

	private Stack<WTask> aStack; // Pile des activités à effectuer
	private WTask ctask; // Une activité

	private int idBase;
	private double angleBase;
	private double distanceBase;
	private double angleToHeading;
	private double distanceToHeading;
	private boolean baseNeedFood;

	private boolean enemyBaseSpotted = false;
	private boolean someoneIsInCharge = false;
	private int attackLeaderID = -1;

	public WarExplorerBrainController() {
		super();
		ctask = searchFood;
		aStack = new Stack<WTask>();
		aStack.push(ctask);

	}

	public String action() {

		// Traitement des messages
		handlingMessages();

		// Execution des reflexes s'il y en a
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

		baseNeedFood = false;

		// Traitement des messages reçus
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("baseInfoResponse")) {
				idBase = Integer.parseInt(msg.getContent()[0]);
				angleBase = msg.getAngle();
				distanceBase = msg.getDistance();
			} else if (msg.getMessage().equals("baseNeedFood") && !isBagEmpty()) {
				idBase = Integer.parseInt(msg.getContent()[0]);
				angleBase = msg.getAngle();
				distanceBase = msg.getDistance();
				baseNeedFood = true;
			} else if (msg.getMessage().equals("ImLeadingTheAttack")) {
				if(!someoneIsInCharge) {
						attackLeaderID = Integer.valueOf(msg.getContent()[0]);
						someoneIsInCharge = true;
						// System.out.println("Explorer " + getID() + " received " + msg.getMessage() + " from leader " + attackLeaderID);
				}
			}
		}

		// Message a� envoyer selon la perception
		for (WarAgentPercept p : getPercepts()) {
			if (p.getType() == WarAgentType.WarFood && isBagFull()) {
				Vector2 explorerToFood = VUtils.cartFromPolaire(p.getAngle(), p.getDistance());
				String coord[] = { explorerToFood.x + "", explorerToFood.y + "" };
				broadcastMessageToAgentType(WarAgentType.WarExplorer, "foodFind", coord);
			} else if (p.getType() == WarAgentType.WarFood && !isBagFull() && baseNeedFood) {
				angleToHeading = p.getAngle();
				aStack.push(ctask);
				ctask = searchFood;
			} else if (p.getType() == WarAgentType.WarBase && isEnemy(p)) {

				Vector2 explorerToBaseEnemy = VUtils.cartFromPolaire(p.getAngle(), p.getDistance());
				String coord[] = { explorerToBaseEnemy.x + "", explorerToBaseEnemy.y + "" };

				// Si personne n'est en charge de l'attaque
				if (!someoneIsInCharge) { 
					requestRole("Soldiers", "chief");
					attackLeaderID = getID();
					someoneIsInCharge = true;
					enemyBaseSpotted = true;

					broadcastMessageToGroup("Soldiers", "enemyBaseSpotted", coord);
					broadcastMessageToAgentType(WarAgentType.WarBase, "enemyBaseSpotted", coord);
					broadcastMessageToAgentType(WarAgentType.WarExplorer, "ImLeadingTheAttack", getID() + "");

					aStack.push(ctask);
					ctask = leadTheAttack;
				} // Si je suis en charge de l'attaque 
				else if (attackLeaderID == getID()) {
					// System.out.println("Explorer " + getID() + " is in charge");
					broadcastMessageToGroup("Soldiers", "enemyBaseSpotted", coord);
					broadcastMessageToAgentType(WarAgentType.WarExplorer, "ImLeadingTheAttack", getID() + "");
				}
			}
		}

		broadcastMessageToAgentType(WarAgentType.WarBase, "baseInfoAnswer", "");

	}

	/*******************************************************
	 ******************** REFLEXES *************************
	 *******************************************************/
	public String doReflexes() {

		if (getHealth() < getMaxHealth() && !isBagEmpty())
			return ACTION_EAT;

		for (WarAgentPercept percept : getPerceptsAllies()) {
			if (toCloseFromFriend(percept) && !(percept.getType() == WarAgentType.WarBase)
					&& !(attackLeaderID == getID())) {
				setRandomHeading();
				return ACTION_MOVE;
			}
		}

		return null;
	}

	public boolean toCloseFromFriend(WarAgentPercept percept) {
		return percept.getDistance() <= (WarExplorer.DISTANCE_OF_VIEW / 10) && percept.getDistance() != 0;
	}

	/*******************************************************
	 ********************* ACTIVITES ***********************
	 *******************************************************/

	static WTask healMySelfTask = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarExplorerBrainController me = (WarExplorerBrainController) ec;

			me.setDebugString("Heal me");

			if (me.isHealthGood()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.getNbElementsInBag() == 0) {
				return me.idle();
			} else if (me.isBagFull()) {
				me.aStack.push(me.ctask);
				me.ctask = bringFoodToBase;
			}

			return me.eat();
		}
	};

	static WTask searchFood = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarExplorerBrainController me = (WarExplorerBrainController) ec;

			me.setDebugString("Search food");

			if (me.isBlocked()) {
				me.setRandomHeading();
			} else if (me.isHealthCritic()) {
				me.aStack.push(me.ctask);
				me.ctask = healMySelfTask;
			} else if (me.isBagFull()) {
				me.aStack.push(me.ctask);
				me.ctask = bringFoodToBase;
			} else if (me.foodPercept() != -1 && me.foodPercept() <= WarFood.MAX_DISTANCE_TAKE) {
				return me.take();
			} else if (me.foodPercept() != -1 && me.foodPercept() > WarFood.MAX_DISTANCE_TAKE) {
				me.setHeading(me.angleToHeading);
			} else if (me.foodPercept() == -1 && me.otherExplorerFindFood()) {
				me.setHeading(me.angleToHeading);
			}

			return me.move();
		}
	};

	static WTask bringFoodToBase = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarExplorerBrainController me = (WarExplorerBrainController) ec;

			me.setDebugString("Bring food to base");

			if (me.isBagEmpty()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.isBlocked()) {
				me.setRandomHeading();
			} else if (me.isHealthCritic()) {
				me.aStack.push(me.ctask);
				me.ctask = healMySelfTask;
			} else if (me.foodPercept() != -1 && !me.isBagFull()) {
				me.setHeading(me.angleToHeading);
			} else if (me.distanceBase < WarFood.MAX_DISTANCE_TAKE) {
				me.setIdNextAgentToGive(me.idBase);
				return me.give();
			} else if (me.distanceBase > WarFood.MAX_DISTANCE_TAKE) {
				me.setHeading(me.angleBase);
			}

			return me.move();
		}
	};

	WTask leadTheAttack = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarExplorerBrainController me = (WarExplorerBrainController) ec;

			me.setDebugString("I'm leading the attack");

			if (me.isHealthCritic()) {
				me.aStack.push(me.ctask);
				me.ctask = healMySelfTask;
			}

			return me.idle();
		}
	};

	/*******************************************************
	 *********** CONDITIONS CHANGEMENT ACTIVITE ************
	 *******************************************************/

	public double foodPercept() {
		for (WarAgentPercept p : getPercepts()) {
			if (p.getType() == WarAgentType.WarFood) {
				angleToHeading = p.getAngle();
				return p.getDistance();
			}
		}
		return -1;
	}

	public boolean otherExplorerFindFood() {
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("foodFind")) {
				Vector2 exToFood = new Vector2(Float.valueOf(msg.getContent()[0]), Float.valueOf(msg.getContent()[1]));
				Vector2 meToEx = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
				Vector2 meToFood = meToEx.add(exToFood);

				angleToHeading = VUtils.polaireFromCart(meToFood).x;
				distanceToHeading = VUtils.polaireFromCart(meToFood).y;

				return true;
			}
		}
		return false;
	}

	public boolean baseNeedFood() {
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("baseNeedFood")) {
				return true;
			}
		}
		return false;
	}

	public boolean isHealthGood() {
		return getHealth() >= 0.8 * getMaxHealth();
	}

	public boolean isHealthCritic() {
		return getHealth() < 0.5 * getMaxHealth();
	}

}
