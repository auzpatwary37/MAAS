package calibration;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.TypicalDurationScoreComputation;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.xml.sax.SAXException;

import com.google.common.collect.Maps;

import createPTGTFS.FareCalculatorPTGTFS;
import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;
import maasPackagesV2.MaaSPackagesWriter;
import optimizerAgent.MaaSUtil;
import optimizerAgent.ObjectiveAndGradientCalculator;
import optimizerAgent.PersonPlanSueModel;
import ust.hk.praisehk.metamodelcalibration.Utils.MatlabObj;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.calibrator.ObjectiveCalculator;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class BobyqaObjective implements MultivariateFunction, MatlabObj,Calcfc{
	
	private final LinkedHashMap<String,Double> initialParam;
	private final ParamReader pReader;
	private PersonPlanSueModel sue;
	private final Measurements measurements;
	private final String fileLoc;
	private int iterCounter=0;
	private double[] lowerBound=null;
	private double[] upperBound=null;
	private LinkedHashMap<String,Double> ParamMultiplier=new LinkedHashMap<>();
	private final Population population;
	
	
	
	public double[] getLowerBound() {
		return lowerBound;
	}

	public void setLowerBound(double[] lowerBound) {
		this.lowerBound = lowerBound;
	}

	public double[] getUpperBound() {
		return upperBound;
	}

	public void setUpperBound(double[] upperBound) {
		this.upperBound = upperBound;
	}

	public BobyqaObjective(LinkedHashMap<String,Double> param,ParamReader pReader,PersonPlanSueModel sue,Measurements measurements,String fileLoc, Population population) {
		this.initialParam=param;
		this.pReader=pReader;
		this.sue=sue;
		this.measurements=measurements;
		this.fileLoc=fileLoc;
		this.population = population;
		for(String s:this.initialParam.keySet()) {
			this.ParamMultiplier.put(s, 1.);
		}
	}
	
	public BobyqaObjective(LinkedHashMap<String,Double> param,ParamReader pReader,PersonPlanSueModel sue,Measurements measurements,String fileLoc,String timeId,LinkedHashMap<String,Double> paramMultiplier, Population population) {
		this.initialParam=param;
		this.pReader=pReader;
		this.sue=sue;
		this.measurements=measurements;
		this.population = population;
		this.fileLoc=fileLoc;
		this.ParamMultiplier=paramMultiplier;
	}
	
	@Override
	public double value(double[] x) {
		LinkedHashMap<String, Double>params=ScaleUp(x);
		for(String s:params.keySet()) {
			params.put(s,params.get(s)*this.ParamMultiplier.get(s));
		}
		//this.sue.clearLinkCarandTransitVolume();
		pReader.SetParamToConfig(sue.getScenario().getConfig(), params);
		Measurements anaMeasurements=this.measurements.clone();
		anaMeasurements=this.sue.performAssignment(population, params, this.measurements);
		new MeasurementsWriter(anaMeasurements).write(fileLoc+"/measurements"+iterCounter+".xml");
		double Objective=ObjectiveCalculator.calcObjective(this.measurements, anaMeasurements, ObjectiveCalculator.TypeMeasurementAndTimeSpecific);
		this.logOoptimizationDetails(this.iterCounter, this.fileLoc, params, Objective);
		iterCounter++;
		return Objective;
	}
	
//	@Override
//	public LinkedHashMap<String,Double> ScaleUp(double[] x) {
//		LinkedHashMap<String,Double> params=new LinkedHashMap<>();
//		int j=0;
//		for(String s:this.initialParam.keySet()) {
//			params.put(s, (1+x[j]/100)*this.initialParam.get(s));
////			params.put(s, x[j]);
//			j++;
//		}
//
//		return params;
//	}
	private void logOoptimizationDetails(int optimIterNo,String fileLoc,LinkedHashMap<String,Double>params,double objective) {
		System.out.println("Objective for timeBean "+"and iteration " + optimIterNo +" = "+objective);
		try {
			File file=new File(fileLoc+"_OoptimizationDetails.csv");
			FileWriter fw=new FileWriter(file,true);
			if(optimIterNo==0) {
				fw.append("optimIterNo,Objective");
				for(String s:params.keySet()) {
					fw.append(","+s);
				}
				fw.append("\n");
			}
			fw.append(optimIterNo+","+objective);
			for(double d:params.values()) {
				fw.append(","+d);
			}
			fw.append("\n");

			fw.flush();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public double evaluateFunction(double[] x) {
		LinkedHashMap<String, Double>params=ScaleUp(x);
		for(String s:params.keySet()) {
			params.put(s,params.get(s)*this.ParamMultiplier.get(s));
		}
//		this.sue.clearLinkCarandTransitVolume();
		Measurements anaMeasurements=this.measurements.clone();
		anaMeasurements=this.sue.performAssignment(population, new LinkedHashMap<>(params), anaMeasurements);
		new MeasurementsWriter(anaMeasurements).write(fileLoc+"/measurements"+iterCounter+".xml");
		double Objective=ObjectiveCalculator.calcObjective(this.measurements, anaMeasurements, ObjectiveCalculator.TypeMeasurementAndTimeSpecific);
		this.logOoptimizationDetails(this.iterCounter, this.fileLoc, params, Objective);
		iterCounter++;
		return Objective;
	}

	@Override
	public double evaluateConstrain(double[] x) {
		// TODO Auto-generated method stub
		return 5;
	}

	@Override
	public double compute(int n, int m, double[] x, double[] con) {
		LinkedHashMap<String, Double>params=ScaleUp(x);
		for(String s:params.keySet()) {
			params.put(s,params.get(s)*this.ParamMultiplier.get(s));
		}
//		this.sue.clearLinkCarandTransitVolume();
		if(sue.getConfig()==null) sue.setConfig(sue.getScenario().getConfig());
		Config c = pReader.SetParamToConfig(sue.getConfig(),params);
		sue.setConfig(c);
		Measurements anaMeasurements=this.measurements.clone();

		this.sue.setCalculateGradient(false);
		if(iterCounter!=0)sue.setNewPop(false);
		anaMeasurements=this.sue.performAssignment(population, new LinkedHashMap<>(params), anaMeasurements);
//		double highUseLink=((CNLSUEModel)sue).getHighUseLink();
		this.writeMeasurementComparison(fileLoc, this.measurements, anaMeasurements, iterCounter);
		new MeasurementsWriter(anaMeasurements).write(fileLoc+"measurements"+iterCounter+".xml");
		double Objective=ObjectiveCalculator.calcGEHObjective(this.measurements, anaMeasurements, ObjectiveCalculator.TypeMeasurementAndTimeSpecific);
		this.logOoptimizationDetails(this.iterCounter, this.fileLoc, params, Objective);
		//if(highUseLink<.95)highUseLink=.95;
//		Objective=Objective*highUseLink;
//		System.out.println(highUseLink);
		iterCounter++;
		double[] y = new double[x.length];
		int ii = 0;
		for(double v:params.values()) {
			y[ii] = v;
			ii++;
		}
		int k=0;
		for(int j=0;j<n;j++) {
			con[k]=y[j]-this.lowerBound[j];
			con[k+1]=this.upperBound[j]-y[j];
			k=k+2;
		}
		return Objective;
	}
	
	public static void main(String[] args) {
		
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";

		ObjectAttributes objATT = new ObjectAttributes();
		new ObjectAttributesXmlReader(objATT).readFile("new Data/core/personAttributesHKI.xml");
		Measurements mm = new MeasurementsReader().readMeasurements("test\\GovtBreakEven\\ATCMeasurementsPeakFuLLHK.xml");
		ParamReader pReader = new ParamReader("test\\GovtBreakEven2\\subPopParamAndLimit.csv");
		pReader.setAllowUnkownParamaeterWhileScalingUp(true);
		LinkedHashMap<String,Double> params = pReader.getInitialParam();
		LinkedHashMap<String,Tuple<Double,Double>> paramLimits = pReader.getInitialParamLimit();
		String popLoc = "test/GovtBreakEven/withoutMaaSPopulationMay27Allpac.xml";
		String maasOwner = "Govt";
		String MaaSPacakgeFileLoc = "test/packages_July2020_20.xml";
		String newMaaSWriteLoc = "test/packages_"+maasOwner+".xml";
		String averageDurationMapFileLoc = "test/actAverageDurations.csv";
		String resultWriteLoc = "test/GovtBreakEven2/Calibration/";
		Config config = singlePlanAlgo.RunUtils.provideConfig();
		config.plans().setInputFile(popLoc);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		TransitSchedule ts = scenario.getTransitSchedule();
		int trLines = 0;
		int trRoutes = 0;
		int busLines = 0;
		int busRoutes = 0;
		int ferryLines = 0;
		int ferryRoutes = 0;
		for(Entry<Id<TransitLine>, TransitLine> d:ts.getTransitLines().entrySet()) {
			String mode = "";
			for(Entry<Id<TransitRoute>, TransitRoute> g:d.getValue().getRoutes().entrySet()) {
				mode = g.getValue().getTransportMode();
				if(mode.equals("ferry"))ferryRoutes++;
				else if(mode.equals("train"))trRoutes++;
				else if(mode.equals("bus"))busRoutes++;
			}
			if(mode.equals("ferry"))ferryLines++;
			else if(mode.equals("train"))trLines++;
			else if(mode.equals("bus"))busLines++;
		}
		System.out.println( "trLines = "+ trLines);
		System.out.println( "trRoutes = "+ trRoutes);
		System.out.println( "busLines = "+ busLines);
		System.out.println( "busRoutes = "+ busRoutes);
		System.out.println( "ferryLines = "+ ferryLines);
		System.out.println( "ferryRoutes = "+ ferryRoutes);
		
		Map<String,Double>avgDur = ObjectiveAndGradientCalculator.readSimpleMap(averageDurationMapFileLoc,true);
		for(Person person: scenario.getPopulation().getPersons().values()) {
			String subPop = (String) objATT.getAttribute(person.getId().toString(), "SUBPOP_ATTRIB_NAME");
			PopulationUtils.putSubpopulation(person, subPop);
			Id<Vehicle> vehId = Id.create(person.getId().toString(), Vehicle.class);
			Map<String, Id<Vehicle>> modeToVehicle = Maps.newHashMap();
			modeToVehicle.put("taxi", vehId);
			modeToVehicle.put("car", vehId);
			VehicleUtils.insertVehicleIdsIntoAttributes(person, modeToVehicle);
			for(PlanElement pe :person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof Activity) {
					Activity act = (Activity)pe;
					
					if(scenario.getConfig().planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getType()) == null) {
						ActivityParams paramsAct = new ActivityParams(act.getType());
						paramsAct.setTypicalDurationScoreComputation(TypicalDurationScoreComputation.uniform);
						paramsAct.setMinimalDuration(3600);
						if(avgDur.get(act.getType())!=null||avgDur.get(act.getType())!=0) {
							paramsAct.setTypicalDuration(avgDur.get(act.getType()));
						}
						else {
							paramsAct.setTypicalDuration(3600);
						}
						if(act.getType().equalsIgnoreCase("Place nearby to home / downstairs (please specify)")) {
							System.out.println();
						}
						config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(paramsAct);
						config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(paramsAct);
						config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(paramsAct);
						config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(paramsAct);
						config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(paramsAct);
					}else {
						if(act.getType().equals("Place nearby to home / downstairs")) {
							System.out.println();
						}
					}
				}
			}
			
			
		}
		Set<Id<Measurement>>removeId = new HashSet<>();
		mm.getMeasurements().values().forEach(m->{
			List<Id<Link>> linkList = (List<Id<Link>>) m.getAttributes().get(Measurement.linkListAttributeName);
			linkList.forEach(l->{
				if(!scenario.getNetwork().getLinks().containsKey(l)) {
					removeId.add(m.getId());
					return;
				}
			});
		});
		removeId.forEach(r->mm.removeMeasurement(r));
		
		MaaSPackages pac = new MaaSPackagesReader().readPackagesFile(MaaSPacakgeFileLoc);//Read the original
		MaaSPackages pacAll = null;
		//converting to operator unified
		if(maasOwner!=null) {
			pacAll = MaaSUtil.createUnifiedMaaSPackages(pac, maasOwner, "allPack");
		
		
			pac = null;
			pacAll.setAllOPeratorReimbursementRatio(1.);//set reimbursement ratio
			pacAll.getMassPackagesPerOperator().get(maasOwner).forEach(m->m.getOperatorReimburesementRatio().put(maasOwner, 1.0));
			new MaaSPackagesWriter(pacAll).write(newMaaSWriteLoc);//write down the modified packages
		}else {
			pacAll = pac;
			new MaaSPackagesWriter(pacAll).write(newMaaSWriteLoc);
		}
		
		Map<String,FareCalculator>fareCalculators = new HashMap<>();
		SAXParser saxParser;
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("new Data/data/busFare.xml", busFareGetter);
			
		} catch (ParserConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			fareCalculators.put("train", new MTRFareCalculator("fare/mtr_lines_fares.csv",scenario.getTransitSchedule()));
			fareCalculators.put("bus", FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/busFareGTFS.json"));
			fareCalculators.put("minibus", busFareGetter.get());
			fareCalculators.put("LR", new LRFareCalculator("fare/light_rail_fares.csv"));
			fareCalculators.put("ferry",FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/ferryFareGTFS.json"));
			fareCalculators.put("tram", new UniformFareCalculator(2.6));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		double startTime = config.qsim().getStartTime().seconds();
		double endTime = config.qsim().getEndTime().seconds();
		Map<String,Tuple<Double,Double>> timeBeans = new HashMap<>();
		int hour = ((int)startTime/3600)+1;
		for(double ii = 0; ii < endTime; ii = ii+3600) {
			timeBeans.put(Integer.toString(hour), new Tuple<>(ii,ii+3600));
			hour = hour + 1;
		}
		
		
		PersonPlanSueModel model = new PersonPlanSueModel(timeBeans, config);
		
		model.populateModel(scenario, fareCalculators, pacAll);
		model.setCalculateGradient(false);
//		params.putAll(model.getInternalParamters());
//		paramLimits.putAll(model.getAnalyticalModelParamsLimit());
		mm.applyFator(.1);
		BobyqaObjective objective = new BobyqaObjective(params, pReader, model, mm, resultWriteLoc, scenario.getPopulation());
		
		double[] xL = new double[params.size()];
		double[] xU = new double[params.size()];
		
		int i = 0;
		for(String k:params.keySet()) {
		
			xL[i] = paramLimits.get(k).getFirst();
			xU[i] = paramLimits.get(k).getSecond();
			i++;
		}
		objective.setLowerBound(xL);
		objective.setUpperBound(xU);
		System.out.println(params);
		double[] x = objective.scaleDownExperimental(params);
		System.out.println(objective.ScaleUp(x));
		
		CobylaExitStatus result1= Cobyla.findMinimum(objective,x.length, x.length*2,
				x,30,.001 ,3, 100);
		
		System.out.println(params);
		System.out.println(paramLimits);
		
	}
	
	public double[] scaleDownExperimental(LinkedHashMap<String,Double> x) {
		double[] y = new double[x.size()] ;
		int i = 0;
		for(Entry<String, Double> d: x.entrySet()) {
			y[i] = 100/(this.upperBound[i]-this.lowerBound[i])*(d.getValue()-this.lowerBound[i]);
			i++;
		}
		return y;
	}
	
	@Override
	public LinkedHashMap<String, Double> ScaleUp(double [] x) {
		double[] y = new double[x.length] ;
		for(int i = 0;i<x.length;i++) {
			y[i] = (this.upperBound[i]-this.lowerBound[i])/100*x[i]+this.lowerBound[i];
		}
		LinkedHashMap<String,Double> outParam = new LinkedHashMap<>();
		int i = 0;
		for(String s:this.initialParam.keySet()) {
			outParam.put(s, y[i]);
			i++;
		}
		return outParam;
	}

	public void writeMeasurementComparison(String fileLoc,Measurements Original, Measurements toCompare,int iterationNo) {
		try {
			FileWriter fw=new FileWriter(new File(fileLoc+"Comparison"+iterationNo+".csv"),false);
			fw.append("MeasurementId,timeBeanId,RealCount,currentSimCount\n");
			for(Measurement m: Original.getMeasurements().values()) {
				for(String timeBean:m.getVolumes().keySet()) {
					
					fw.append(m.getId()+","+timeBean+","+Original.getMeasurements().get(m.getId()).getVolumes().get(timeBean)+","+
				toCompare.getMeasurements().get(m.getId()).getVolumes().get(timeBean)+"\n");
					}
			}
		fw.flush();
		fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
