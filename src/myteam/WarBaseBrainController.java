package myteam;

import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.brains.brains.WarBaseBrain;
import edu.warbot.communications.WarMessage;
import edu.warbot.fsm.WarEtat;
import edu.warbot.fsm.WarFSM;
import edu.warbot.fsm.condition.WarCondition;

import java.util.List;
import java.lang.reflect.Method;

public abstract class WarBaseBrainController extends WarBaseBrain {

	private WarEtat<WarBaseBrainController> ctask;
	private WarCondition<WarBaseBrainController> conds;
	

	public WarBaseBrainController() {
		super();
		
	}
	
	
	
	@Override
	public final void activate() {
		
	}

	@Override
	public String action() {
		
		return fsm.executeFSM();

	}

	public void method1() throws NoSuchMethodException {
		setDebugString("base");

		if (isAbleToCreate(WarAgentType.WarExplorer)) {
			System.out.println("Create = " + isAbleToCreate(WarAgentType.WarExplorer) + " Pt de vie de la base = "
					+ getHealth() + " sur " + getMaxHealth());
			setNextAgentToCreate(WarAgentType.WarExplorer);
		
		} else if (getNbElementsInBag() > 0) {
			System.out.println("Je mange. Pt de vie de la base = " + getHealth() + " sur " + getMaxHealth());
		
		} else {
			
		}

		// List<WarMessage> msgs = getMessages();
		// for (WarMessage msg : msgs) {
		// if (msg.getMessage().equals("Give me your ID base")) {
		// setDebugString("I'm here");
		// reply(msg, "I am the base and here is my ID",
		// Integer.toString(getID()));
		// } else if (msg.getMessage().equals("Enemy base spotted")) {
		// setDebugString("Explorers spotted the enemy base");

		// Vector2 explorerToEBase = new
		// Vector2(Float.valueOf(msg.getContent()[0]),
		// Float.valueOf(msg.getContent()[1]));
		// Vector2 baseToEx = VUtils.cartFromPolaire(msg.getAngle(),
		// msg.getDistance());
		// Vector2 baseToEBase = baseToEx.add(explorerToEBase);
		// String coord [] = {baseToEBase.x + "", baseToEBase.y + ""};
		//
		// broadcastMessageToAgentType(WarAgentType.WarRocketLauncher, "Attack
		// the enemy base", coord);
		// }
		// }

		// return idle();

	}

}
