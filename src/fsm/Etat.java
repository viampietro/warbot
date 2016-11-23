package fsm;

import java.util.List;

public abstract class Etat {
	Agent ag;
	List<Transition> transitions;

	void exec(Agent ag){
		boolean tvalide = false;
		for (Transition x : transitions)
			if (x.valide()){
				x.execTransition();
				tvalide=true; break;
			}
		
		if (!tvalide)
			this.activite();
}

	abstract void activite();

	void etatFinal() {
		return false;
	}
}