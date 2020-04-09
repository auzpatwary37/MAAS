package optimizerAgent;

import java.util.Random;
import java.util.Map.Entry;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import singlePlanAlgo.MAASPackages;

public class MAASOperatorModule implements PlanStrategyModule{
	
	private Random rnd;
	@Inject
	private @Named("MAASPackages") MAASPackages packages;
	
	private double distanceScale = 1; 
	
	private double updateStepSizeAfterIteration = 5;// Make this a parameter in the optimization module
	
	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		// TODO Auto-generated method stub
		int n = (int) (replanningContext.getIteration()/this.updateStepSizeAfterIteration);
		this.distanceScale = 1/(n+1);
	}

	
	@Override
	public void handlePlan(Plan plan) {
		//Basically given a plan and current scores, the operator will take some decisions here. 
		// Because the plan will be a maas agent plan, it is attributed with variableDetails
		//Each variable details have basically current parameter values and the limit of parameter values.
		// For now we test random case
		// Finally he will decide new operation parameter
		//For a base case scenario let us apply the random now 
		
		for(Entry<String, Object> e :plan.getAttributes().getAsMap().entrySet()) {
			if(e.getValue() instanceof VariableDetails) {
				VariableDetails v = (VariableDetails)e.getValue();
				double current = v.getCurrentValue();
				
				Random rnd = new Random();
				
				int sign = -1;
				if(rnd.nextBoolean()) sign = 1;
				
				double newCost = current+sign * (v.getLimit().getSecond() - v.getLimit().getFirst())*rnd.nextDouble()*this.distanceScale;
				
				v.setCurrentValue(newCost);
				
				this.packages.getMassPackages().get(e.getKey()).setPackageCost(newCost);
			}
		}
		
		
		
		
	}

	@Override
	public void finishReplanning() {
		// TODO Auto-generated method stub
		
	}

}
