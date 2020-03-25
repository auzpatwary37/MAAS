package optimizerAgent;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

public class MAASOperatorModule implements PlanStrategyModule{

	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		// TODO Auto-generated method stub
		
	}

	
	@Override
	public void handlePlan(Plan plan) {
		//Basically given a plan and current scores, the operator will take some decisions here. 
		// Because the plan will be a maas agent plan, it is attributed with variableDetails
		//Each variable details have basically current parameter values and the limit of parameter values.
		// For now we test random case
		// Finally he will decide new operation parameter
		//For a base case scenario
		
		
		
		
	}

	@Override
	public void finishReplanning() {
		// TODO Auto-generated method stub
		
	}

}
