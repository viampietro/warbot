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
import java.util.Stack;

import com.android.org.bouncycastle.util.Integers;
import com.google.common.primitives.Ints;
import com.sun.org.apache.xalan.internal.xsltc.util.IntegerArray;

public abstract class WarBaseBrainController extends WarBaseBrain {

	private Stack<WTask> aStack; // Pile des activités à effectuer
	private WTask ctask; // Une activité

	public WarBaseBrainController() {
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
			if (msg.getMessage().equals("baseInfoAnswer")) {
				reply(msg, "baseInfoResponse", Integer.toString(getID()));
			} else if (msg.getMessage().equals("enemyBaseSpotted")) {
				aStack.push(ctask);
				ctask = createSoldierTask;
			}
		}

		// Message à envoyer selon la perception
		for (WarAgentPercept p : getPercepts()) {
			if (isEnemy(p)) {
				broadcastMessageToAll("IamSpotted", "");
			}
		}

		// Message à envoyer selon l'état
		if (isBagEmpty())
			broadcastMessageToAgentType(WarAgentType.WarExplorer, "baseNeedFood", Integer.toString(getID()));

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

			if (me.isEnemyBaseSpotted()) {
				me.ctask = createSoldierTask;
			} else if (me.isHealthCritic()) {
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
			} else {
				int nbEachSoldierRoles[] = { me.getNumberOfAgentsInRole("Soldiers", "RocketLauncher"),
						me.getNumberOfAgentsInRole("Soldiers", "Light"),
						me.getNumberOfAgentsInRole("Soldiers", "Heavy") };
				int indexMin = Ints.indexOf(nbEachSoldierRoles, Ints.min(nbEachSoldierRoles));

				switch (indexMin) {
				case 0:
					if (me.getMaxHealth() * 0.45 < me.getHealth() - WarRocketLauncher.COST)
						me.setNextAgentToCreate(WarAgentType.WarRocketLauncher);
					break;
				case 1:
					if (me.getMaxHealth() * 0.45 < me.getHealth() - WarLight.COST)
						me.setNextAgentToCreate(WarAgentType.WarLight);
					break;
				case 2:
					if (me.getMaxHealth() * 0.45 < me.getHealth() - WarHeavy.COST)
						me.setNextAgentToCreate(WarAgentType.WarHeavy);
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

	public boolean isEnemyBaseSpotted() {
		for (WarMessage msg : getMessages()) {

			if (msg.getMessage().equals("enemyBaseSpotted")) {
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
		return getHealth() > 0.8 * getMaxHealth();
	}

}
