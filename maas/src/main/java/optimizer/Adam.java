package optimizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;

import optimizerAgent.VariableDetails;


public class Adam implements Optimizer{

	private double alpha = .1;
	private double beta1 = .9;
	private double beta2 = 0.999;
	private double eta = 10e-8;
	private Map<String,VariableDetails> variables = new HashMap<>();
	private Map<String,Double> m = new HashMap<>();
	private Map<String,Double> v = new HashMap<>();
	private int counter;
	private final String id;
	private static final Logger logger = Logger.getLogger(Adam.class);
	
	public Adam(Plan maasAgentPlan) {
		this.id = maasAgentPlan.getPerson().getId().toString();
		for(Entry<String, Object> a:maasAgentPlan.getAttributes().getAsMap().entrySet()){
			if(a.getValue() instanceof VariableDetails) this.variables.put(a.getKey(), (VariableDetails)a.getValue());
		}
		this.initialize();
	}

	public Adam(String id, Map<String,VariableDetails> variables) {
		this.variables = variables;
		this.id = id;
		this.initialize();
	}
	
	public Adam(String id, Map<String,VariableDetails> variables,double alpha,double beta1,double beta2,double eta) {
		this.variables = variables;
		this.alpha = alpha;
		this.beta1 = beta1;
		this.beta2 = beta2;
		this.eta = eta;
		this.id = id;
		this.initialize();
	}
	
	private void initialize() {
		this.variables.keySet().forEach(var->{
			m.put(var, 0.);
			v.put(var,0.);
			counter = 0;
		});
	}

	public Map<String,VariableDetails> takeStep(Map<String,Double> gradient){
		counter = counter+1;
		if(gradient==null) {
			logger.debug("Gradient is null");
		}
		//for(Entry<String, Double> g:gradient.entrySet()) {
		gradient.entrySet().parallelStream().forEach(g->{
			m.compute(g.getKey(), (k,v)->this.beta1*v+(1-beta1)*g.getValue());
			v.compute(g.getKey(), (k,v)->this.beta2*v+(1-this.beta2)*g.getValue()*g.getValue());
			double m_h = m.get(g.getKey())/(1-this.beta1);
			double v_h = v.get(g.getKey())/(1-this.beta2);
			double var = this.variables.get(g.getKey()).getCurrentValue() + this.alpha*m_h/((Math.sqrt(v_h))+this.eta);
			Tuple<Double,Double> limit = this.variables.get(g.getKey()).getLimit();
			if(var<limit.getFirst()) var = limit.getFirst();
			else if (var>limit.getSecond()) var = limit.getSecond();
			this.variables.get(g.getKey()).setCurrentValue(var);
		});
		//}
		
		return this.variables;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public Map<String, VariableDetails> getVarables() {
		return this.variables;
	}

	@Override
	public void reset() {
		this.initialize();
	}
	
	
	
}
