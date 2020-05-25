package optimizerAgent;

import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.attributable.Attributes;

public class MaaSOperator extends OptimizingAgent{

	public static String type = "MaaSOperator";
	
	public MaaSOperator(Person person, Map<String, Double> variableIntialValue,
			Map<String, Tuple<Double, Double>> variableRange, Activity act) {
		super(person, type,act, variableIntialValue, variableRange);
		
	}

	@Override
	public Map<String, Object> getCustomAttributes() {
		return super.person.getCustomAttributes();
	}

	@Override
	public Attributes getAttributes() {
		return super.person.getAttributes();
	}

	@Override
	public List<? extends Plan> getPlans() {
		return super.person.getPlans();
	}

	@Override
	public boolean addPlan(Plan p) {
		return super.person.addPlan(p);
	}

	@Override
	public boolean removePlan(Plan p) {
		return super.person.removePlan(p);
	}

	@Override
	public Plan getSelectedPlan() {
		return super.person.getSelectedPlan();
	}

	@Override
	public void setSelectedPlan(Plan selectedPlan) {
		super.person.setSelectedPlan(selectedPlan);
		
	}

	@Override
	public Plan createCopyOfSelectedPlanAndMakeSelected() {
		return super.person.createCopyOfSelectedPlanAndMakeSelected();
	}

	@Override
	public Id<Person> getId() {
		return super.person.getId();
	}

	@Override
	public double getFunctionalScoreApproximation() {//insert the metamodel here
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Map<String, Double> getScoreGradientApproximation() {//Insert the metamodel graident here 
		// TODO Auto-generated method stub
		return null;
	}
	
	
}


