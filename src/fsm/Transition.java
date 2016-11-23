package fsm;

public abstract class Transition {
	Agent ag;
	Etat etat;
	
	void execTransition(){
		ag.changerEtat(etat);
		etat.activite();
	}
	abstract boolean valide();
}
