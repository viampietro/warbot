package myteam;

import edu.warbot.agents.agents.WarKamikaze;
import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.agents.resources.WarFood;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarKamikazeBrain;
import edu.warbot.communications.WarMessage;

import java.util.List;
import java.util.Stack;

public abstract class WarKamikazeBrainController extends WarKamikazeBrain {
	
	private Stack<WTask> aStack; // Pile des activités à effectuer
	private WTask ctask; // Une activité
	
	private int idBase;
	private double angleBase;
	private double distanceBase;
	private double angleToHeadingEnemyBase;
	private double angleToHeadingFood;
	private boolean enemyBaseDetected;

	public WarKamikazeBrainController() {
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


		// Traitement des messages reçus
		for (WarMessage msg : getMessages()) {
			if (msg.getMessage().equals("baseInfoResponse")) {
				
				idBase = Integer.parseInt(msg.getContent()[0]);
				angleBase = msg.getAngle();
				distanceBase = msg.getDistance();
				
			} else if (msg.getMessage().equals("EnemyBaseFound")) {
				
				Vector2 exToEnemyBase = new Vector2(Float.valueOf(msg.getContent()[0]), Float.valueOf(msg.getContent()[1]));
				Vector2 meToEx = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
				Vector2 meToEnemyBase = meToEx.add(exToEnemyBase);

				angleToHeadingEnemyBase = VUtils.polaireFromCart(meToEnemyBase).x;
				enemyBaseDetected = true;
				
			}
		}

		// Message a� envoyer selon la perception
		for (WarAgentPercept p : getPercepts()) {
			
		}

		broadcastMessageToAgentType(WarAgentType.WarBase, "baseInfoAnswer", "");

	}

	
	/*******************************************************
	 ******************** REFLEXES *************************
	 *******************************************************/
	
	public String doReflexes() {
		
		if (isHealthCritic() && !isBagEmpty())
			return ACTION_EAT;
		
		if(perceptEnemyBase() != -1 && perceptEnemyBase() < 1)
			return ACTION_FIRE;
		
		if(enemyBaseDetected){
			setHeading(angleToHeadingEnemyBase);
			return ACTION_MOVE;
		}
			

		return null;
	}

	
	/*******************************************************
	 ********************* ACTIVITES ***********************
	 *******************************************************/
	
	static WTask healMySelfTask = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarKamikazeBrainController me = (WarKamikazeBrainController) ec;

			me.setDebugString("Heal me");

			if (me.isHealthGood()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.getNbElementsInBag() == 0) {
				me.aStack.push(me.ctask);
				me.ctask = searchFoodToHeal;
				return me.idle();
			}

			return me.eat();
		}
	};
	
	static WTask searchFoodToHeal = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarKamikazeBrainController me = (WarKamikazeBrainController) ec;

			me.setDebugString("Search food to heal");

			if (me.isBlocked()) {
				me.setRandomHeading();
			} else if (!me.isHealthCritic()){
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (!me.isBagEmpty()){
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
	
	static WTask searchFood = new WTask() {

		@Override
		String exec(WarBrain ec) {
			WarKamikazeBrainController me = (WarKamikazeBrainController) ec;

			me.setDebugString("Search food");

			if (me.isBlocked()) {
				me.setRandomHeading();
			} else if (me.isHealthCritic()) {
				me.aStack.push(me.ctask);
				me.ctask = healMySelfTask;
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
	
	public boolean isHealthCritic() {
		return getHealth() < 0.6 * getMaxHealth();
	}
	
	public boolean isHealthGood() {
		return getHealth() >= 0.85 * getMaxHealth();
	}
	
	public double perceptEnemyBase(){
		for (WarAgentPercept p : getPercepts()) {
			if(p.getType() == WarAgentType.WarBase && isEnemy(p))
				return p.getDistance();
		}
		return -1;
	}
	
}
