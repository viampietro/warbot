package myteam;

import edu.warbot.agents.agents.WarEngineer;
import edu.warbot.agents.agents.WarExplorer;
import edu.warbot.agents.agents.WarHeavy;
import edu.warbot.agents.agents.WarLight;
import edu.warbot.agents.agents.WarRocketLauncher;
import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarBaseBrain;
import edu.warbot.communications.WarMessage;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Stack;

import com.android.org.bouncycastle.util.Integers;
import com.google.common.primitives.Ints;
import com.sun.org.apache.xalan.internal.xsltc.util.IntegerArray;

public abstract class WarBaseBrainController extends WarBaseBrain {

	private Stack<WTask> aStack; // Pile des activités à effectuer
	private WTask ctask; // Une activité

	private List<WarAgentPercept> percepts;
	private List<WarMessage> messages;

	private boolean enemyBaseSpotted = false;
	private int ticksBeforeCreate = 0;

	private int ticksSinceNoEnemy = 0;
	private static final int ticksToBeSafe = 100;

	private double angleEnemyBase;
	private double distanceEnemyBase;

	public WarBaseBrainController() {
		super();
		ctask = stayIdleTask;
		aStack = new Stack<WTask>();
		aStack.push(ctask);
	}

	public String action() {

		percepts = getPercepts();
		messages = getMessages();

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

		// Traitement des messages recus
		for (WarMessage msg : messages) {
			if (msg.getMessage().equals("baseInfoAnswer")) {
				reply(msg, "baseInfoResponse", Integer.toString(getID()));
			} else if (msg.getMessage().equals("EnemyBaseFound")) {
				setDebugString("Explorers spotted the enemy base");

				if (!enemyBaseSpotted) {
					
					requestRole("Soldiers", "chief");
					
					Vector2 exToEBase = new Vector2(Float.valueOf(msg.getContent()[0]),
							Float.valueOf(msg.getContent()[1]));
					Vector2 baseToEx = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
					Vector2 baseToEBase = baseToEx.add(exToEBase);
					angleEnemyBase = VUtils.polaireFromCart(baseToEBase).x;
					distanceEnemyBase = VUtils.polaireFromCart(baseToEBase).y;

					String coord[] = { angleEnemyBase + "", distanceEnemyBase + "" };
					
					System.out.println("BASE : " + getID() + " angleEB = " + angleEnemyBase + " distanceEnemyBase = " + distanceEnemyBase); 
					broadcastMessageToGroup("Soldiers", "baseEnemyHasFound", coord);

					enemyBaseSpotted = true;
					aStack.push(ctask);
					ctask = createSoldierTask;
				}
			} 
		}

		if (baseIsAttacked()) {
			setDebugString("I'm threatened");
			ticksSinceNoEnemy = 0;
			broadcastMessageToAgentType(WarAgentType.WarRocketLauncher, "baseAttacked", "");
		} else if (ticksSinceNoEnemy < ticksToBeSafe) {
			ticksSinceNoEnemy++;
		} else {
			broadcastMessageToAgentType(WarAgentType.WarRocketLauncher, "baseIsSafe", "");
		}

		// Message a� envoyer selon l'etat
		if (isBagEmpty())
			broadcastMessageToAgentType(WarAgentType.WarExplorer, "baseNeedFood", Integer.toString(getID()));

		if (enemyBaseSpotted) {

			String coord[] = { angleEnemyBase + "", distanceEnemyBase + "" };
			System.out.println("BASE " + getID() + " : angleEB = " + angleEnemyBase + " distanceEnemyBase = " + distanceEnemyBase);
			broadcastMessageToGroup("Soldiers", "baseEnemyHasFound", coord);
		}

	}

	/*******************************************************
	 ******************** REFLEXES *************************
	 *******************************************************/
	public String doReflexes() {

		if (getHealth() < getMaxHealth() && !isBagEmpty())
			return ACTION_EAT;

		return null;
	}

	/*******************************************************
	 ********************* ACTIVITES ***********************
	 *******************************************************/

	static WTask stayIdleTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;

			me.setDebugString("Idle");

			if (me.isHealthCritic()) {
				me.ctask = healMySelfTask;
			}

			return me.idle();
		}
	};

	static WTask healMySelfTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;

			me.setDebugString("Heal me");

			if (me.isHealthGood()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.isBagEmpty())
				return me.idle();

			return me.eat();
		}
	};

	static WTask createSoldierTask = new WTask() {

		@Override
		String exec(WarBrain bc) {
			WarBaseBrainController me = (WarBaseBrainController) bc;

			me.setDebugString("Creation de soldats");

			if (me.isAttackTerminated()) {
				me.ctask = me.aStack.pop();
				return me.idle();
			} else if (me.isHealthCritic()) {
				me.aStack.push(me.ctask);
				me.ctask = healMySelfTask;
				return me.idle();
			} else if (me.ticksBeforeCreate < 50) {
				me.ticksBeforeCreate++;
				return me.idle();
			} else {
				System.out.println("RocketLauncher : " + me.getNumberOfAgentsInRole("Soldiers", "RocketLauncher")
						+ " Light : " + me.getNumberOfAgentsInRole("Soldiers", "Light"));

				int nbEachSoldierRoles[] = { me.getNumberOfAgentsInRole("Soldiers", "RocketLauncher"),
						me.getNumberOfAgentsInRole("Soldiers", "Light") };
				int indexMin = Ints.indexOf(nbEachSoldierRoles, Ints.min(nbEachSoldierRoles));

				switch (indexMin) {
				case 0:
					if (me.getMaxHealth() * 0.45 < me.getHealth() - WarRocketLauncher.COST)
						me.setNextAgentToCreate(WarAgentType.WarRocketLauncher);
					me.ticksBeforeCreate = 0;
					break;
				case 1:
					if (me.getMaxHealth() * 0.45 < me.getHealth() - WarLight.COST)
						me.setNextAgentToCreate(WarAgentType.WarLight);
					me.ticksBeforeCreate = 0;
					break;
				default:
					return me.idle();
				}
			}

			return me.create();
		}
	};

	/*******************************************************
	 *********** CONDITIONS CHANGEMENT ACTIVITE ************
	 *******************************************************/

	public boolean isAttackTerminated() {
		for (WarMessage msg : messages) {
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
		return getHealth() > 0.8 * getMaxHealth();
	}

	public boolean baseIsAttacked() {
		for (WarAgentPercept percept : percepts) {
			if (isEnemy(percept) && (percept.getType() == WarAgentType.WarHeavy
					|| percept.getType() == WarAgentType.WarLight || percept.getType() == WarAgentType.WarRocketLauncher
					|| percept.getType() == WarAgentType.WarKamikaze || percept.getType() == WarAgentType.WarEngineer
					|| percept.getType() == WarAgentType.WarExplorer)) {
				return true;
			}
		}

		return false;
	}

}
