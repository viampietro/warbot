package myteam;

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
	private boolean baseNeedFood;
	private double angleToHeadingFood;
	private boolean enemyBaseOrTurretPercept;
	private boolean enemyBaseFound;
	private int nbTicksBlocked = 0;

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

			} else if (msg.getMessage().equals("baseNeedFood") && (getNbElementsInBag() * 100) / getBagSize() >= 50) {

				idBase = Integer.parseInt(msg.getContent()[0]);
				angleBase = msg.getAngle();
				distanceBase = msg.getDistance();
				baseNeedFood = true;

				if (!ctask.equals(bringFoodToBase)) {
					aStack.push(ctask);
					ctask = bringFoodToBase;
				}

			}
		}

		// Message a� envoyer selon la perception
		for (WarAgentPercept p : getPercepts()) {
			if (p.getType() == WarAgentType.WarFood && isBagFull()) {

				Vector2 explorerToFood = VUtils.cartFromPolaire(p.getAngle(), p.getDistance());
				String coord[] = { explorerToFood.x + "", explorerToFood.y + "" };
				broadcastMessageToAll("foodFind", coord);

			} else if (p.getType() == WarAgentType.WarBase && isEnemy(p)) {

				Vector2 explorerToBaseEnemy = VUtils.cartFromPolaire(p.getAngle(), p.getDistance());
				String coord[] = { explorerToBaseEnemy.x + "", explorerToBaseEnemy.y + "" };
				broadcastMessageToAll("EnemyBaseFound", coord);
				enemyBaseOrTurretPercept = true;
				enemyBaseFound = true;

			} else if (p.getType() == WarAgentType.WarTurret && isEnemy(p)) {

				Vector2 explorerToTurretEnemy = VUtils.cartFromPolaire(p.getAngle(), p.getDistance());
				String coord[] = { explorerToTurretEnemy.x + "", explorerToTurretEnemy.y + "" };
				broadcastMessageToAll("EnemyTurretFound", coord);
				enemyBaseOrTurretPercept = true;

			}
		}

		broadcastMessageToAgentType(WarAgentType.WarBase, "baseInfoAnswer", "");

	}

	/*******************************************************
	 ******************** REFLEXES *************************
	 *******************************************************/

	public String doReflexes() {

		if (isBlocked()) {
			setRandomHeading();
			return ACTION_MOVE;
		}

		if (enemyBaseOrTurretPercept && enemyBaseFound) {
			if (nbTicksBlocked < 5) {
				setHeading((getHeading() + 90) % 360);
				setRandomHeading(180);
				enemyBaseOrTurretPercept = false;
				nbTicksBlocked++;
				return ACTION_MOVE;
			} else {
				return ACTION_MOVE;
			}
		}
		nbTicksBlocked = 0;

		if (isHealthCritic() && !isBagEmpty())
			return ACTION_EAT;

		if (baseNeedFood && !isBagEmpty()) {
			aStack.push(ctask);
			ctask = bringFoodToBase;
		}

		return null;
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
				me.aStack.push(me.ctask);
				me.ctask = searchFoodToHeal;
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
				me.setHeading(me.angleToHeadingFood);
			} else if (me.foodPercept() == -1 && me.otherFindFood()) {
				me.setHeading(me.angleToHeadingFood);
			}

			return me.move();
		}
	};

	static WTask searchFoodToHeal = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarExplorerBrainController me = (WarExplorerBrainController) ec;

			me.setDebugString("Search food to heal");

			if (me.isBlocked()) {
				me.setRandomHeading();
			} else if (!me.isHealthCritic()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (!me.isBagEmpty()) {
				return me.eat();
			} else if (me.foodPercept() != -1 && me.foodPercept() <= WarFood.MAX_DISTANCE_TAKE) {
				return me.take();
			} else if (me.foodPercept() != -1 && me.foodPercept() > WarFood.MAX_DISTANCE_TAKE) {
				me.setHeading(me.angleToHeadingFood);
			} else if (me.foodPercept() == -1 && me.otherFindFood()) {
				me.setHeading(me.angleToHeadingFood);
			}

			return me.move();
		}
	};

	static WTask bringFoodToBase = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarExplorerBrainController me = (WarExplorerBrainController) ec;

			me.setDebugString("Bring food to base");
			
			if (me.isBlocked()) {
				me.setRandomHeading();
			} else if (me.isBagEmpty()) {
				me.ctask = me.aStack.pop();
				me.setRandomHeading();
			} else if (me.isHealthCritic()) {
				me.aStack.push(me.ctask);
				me.ctask = healMySelfTask;
			} else if (me.foodPercept() != -1 && !me.isBagFull() && me.foodPercept() <= WarFood.MAX_DISTANCE_TAKE) {
				me.setHeading(me.angleToHeadingFood);
			} else if (me.distanceBase < WarFood.MAX_DISTANCE_TAKE) {
				me.setIdNextAgentToGive(me.idBase);
				return me.give();
			} else if (me.distanceBase > WarFood.MAX_DISTANCE_TAKE) {
				me.setHeading(me.angleBase);
			}

			return me.move();
		}
	};

	/*******************************************************
	 *********** CONDITIONS CHANGEMENT ACTIVITE ************
	 *******************************************************/

	public double foodPercept() {
		for (WarAgentPercept p : getPercepts()) {
			if (p.getType() == WarAgentType.WarFood) {
				angleToHeadingFood = p.getAngle();
				return p.getDistance();
			}
		}
		return -1;
	}

	public boolean otherFindFood() {
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("foodFind")) {

				Vector2 exToFood = new Vector2(Float.valueOf(msg.getContent()[0]), Float.valueOf(msg.getContent()[1]));
				Vector2 meToEx = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
				Vector2 meToFood = meToEx.add(exToFood);

				angleToHeadingFood = VUtils.polaireFromCart(meToFood).x;

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
		return getHealth() >= 0.85 * getMaxHealth();
	}

	public boolean isHealthCritic() {
		return getHealth() < 0.6 * getMaxHealth();
	}

}
