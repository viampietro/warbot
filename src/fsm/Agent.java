package fsm;

public class Agent {
	void go(){
		while (!etatCourant.etatFinal())
		etatCourant.exec(this);
		}
		void changerEtat(Etat etat){
		etatCourant = etat;
		}
}
