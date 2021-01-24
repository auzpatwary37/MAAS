package optimizerAgent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map.Entry;
import java.io.*;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.*;

import optimizerAgent.MaaSUtil;
import optimizerAgent.PersonPlanSueModel;
import optimizerAgent.VariableDetails;


import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.FareLink;
import ucar.nc2.ft2.coverage.remote.CdmrFeatureProto.CoordSysOrBuilder;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.*;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitTransferLink;

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
/**
 * Main idea is to write a universal objective and gradient calculator taking into account the new fareLink Operator and maas operator construct
 * @author ashraf
 *
 */
public class ObjectiveAndGradientCalculator {

	/**
	 * 
	 * @param flow
	 * @param operator
	 * @param packages
	 * @param fareCalculators
	 * @return
	 */
	public static Map<String,Double> calcRevenueObjective(SUEModelOutput flow, Set<String>operator, MaaSPackages packages, Map<String,FareCalculator> fareCalculators){
		Map<String,Double> obj = operator.stream().collect(Collectors.toMap(k->k, k->0.));
		for(String o: operator) {
			//add the revenue from package price
			for(MaaSPackage pac:packages.getMassPackagesPerOperator().get(o)) {
				if(flow.getMaaSPackageUsage().get(pac.getId())!=null) {
					obj.compute(o,(k,v)->v+flow.getMaaSPackageUsage().get(pac.getId())*pac.getPackageCost());
				}
			}
		}
		for(Entry<String,Map<String,Map<String,Double>>> timeMap:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
			for(Entry<String, Map<String,Double>> maasMap:timeMap.getValue().entrySet()) {
				for(Entry<String,Double> fareLinkMap:maasMap.getValue().entrySet()) {
					FareLink fl = new FareLink(fareLinkMap.getKey());
					String fareLinkOperator = packages.getOperatorId(fl);
					String mode = fl.getMode();
					double fare = 0;
					if(fl.getType().equals(FareLink.NetworkWideFare)) {
						fare = fareCalculators.get(mode).getFares(null, null, fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}else {
						fare = fareCalculators.get(mode).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}
					if(maasMap.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {// no maas 
						if(fareLinkOperator!=null && operator.contains(fareLinkOperator)) {
							double f = fare;
							obj.compute(fareLinkOperator, (k,v)->v = v+f);
						}
					}else {
						double discount = packages.getMassPackages().get(maasMap.getKey()).getDiscountForFareLink(fl); 
						if(discount>0) {
							MaaSPackage pac = packages.getMassPackages().get(maasMap.getKey());
							String maasOperator = pac.getOperatorId();
							double rr = pac.getOperatorReimburesementRatio().get(fareLinkOperator);
							if(fareLinkOperator!=null && operator.contains(fareLinkOperator)) {
								double f = fare+(rr-1)*discount;
								obj.compute(fareLinkOperator, (k,v)->v = v+f);
							}
							if(operator.contains(maasOperator)) {
								double f = -1*rr*discount;
								obj.compute(maasOperator, (k,v)->v = v+f);
							}
						}else {
							if(fareLinkOperator!=null && operator.contains(fareLinkOperator)) {
								double f = fare;
								obj.compute(fareLinkOperator, (k,v)->v = v+f);
								
							}
						}
					}
				}
			}
		}
		
		return obj;
	}
	
	/**
	 * 
	 * @param flow
	 * @param vot_car value of time car //take for the with car sub pop
	 * @param vot_transit value of time transit// take for with transit sub pop
	 * @param vot_wait value of time waiting
	 * @param vom value of money
	 * @return total system travel time in money
	 */
	public static double calcTotalSystemTravelTime(PersonPlanSueModel sue, SUEModelOutput flow, Double vot_car, Double vot_transit, Double vot_wait, Double vom) {
		double totalSystemTT = 0;
		// only link flow 
		double tsltt = 0;
		for(Entry<String,Map<Id<Link>,Map<String,Double>>> net : sue.getLinkGradient().entrySet()) {
			for(Entry<Id<Link>,Map<String,Double>> link:net.getValue().entrySet()) {
				tsltt+=(flow.getLinkVolume().get(net.getKey()).get(link.getKey())-((CNLLink)sue.getNetworks().get(net.getKey()).getLinks().get(link.getKey())).getLinkTransitVolume())*flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
			}
		}
		//only transit direct link flow 
		double tstdltt = 0;
		double tsttltt = 0;
		for(Entry<String,Map<Id<TransitLink>,Double>> trLinks:flow.getLinkTransitVolume().entrySet()) {
			for(Entry<Id<TransitLink>,Double>link:trLinks.getValue().entrySet()) {
				if(flow.getTransitDirectLinkTT().get(trLinks.getKey()).containsKey(link.getKey())){
					tstdltt+=link.getValue()*flow.getTransitDirectLinkTT().get(trLinks.getKey()).get(link.getKey());
				}else {
					tsttltt+=link.getValue()*flow.getTransitTransferLinkTT().get(trLinks.getKey()).get(link.getKey());
				}
			}
		}
		totalSystemTT = tsltt*vot_car/vom+tstdltt*vot_transit/vom+tsttltt*vot_wait/vom;
		return totalSystemTT;
	}
	
	/**
	 * 
	 * @param sue
	 * @param flow
	 * @param variables
	 * @param operators
	 * @param packages
	 * @param fareCalculators
	 * @return
	 */
	public static Map<String,Map<String,Double>> calcRevenueObjectiveGradient(PersonPlanSueModel sue, SUEModelOutput flow, Map<String,Double> variables, Map<String,Map<String,VariableDetails>> operators, MaaSPackages packages, Map<String,FareCalculator> fareCalculators) {
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		operators.keySet().forEach(o->operatorGradient.put(o, variables.keySet().stream().collect(Collectors.toMap(k->k, k->0.))));
		
                                           
		//As the fare link operator never have the right to choose the discount rate and the volume comes directly from the decision variable, the volume can never belong to a farelink operator.
		variables.entrySet().parallelStream().forEach(v->{
			String skey = v.getKey();
			String key = MaaSUtil.retrieveName(skey);
			if(key == "") key = skey;
			String pacakgeId = MaaSUtil.retrievePackageId(skey);
			if(packages.getMassPackages().get(pacakgeId)==null) {
				System.out.print("Debug!!!");
			}
			String operatorId = packages.getMassPackages().get(pacakgeId).getOperatorId();
			double volume = 0;
			
			if(MaaSUtil.ifFareLinkVariableDetails(skey)) {//if the variable is a fare link variable
				FareLink farelink = new FareLink(MaaSUtil.retrieveFareLink(skey));
				for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) volume+=timeFl.getValue().get(pacakgeId).get(farelink.toString());//subtract from gradient
				volume=volume*-1*packages.getMassPackages().get(pacakgeId).getOperatorReimburesementRatio().get(packages.getOperatorId(farelink));
				
			}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(skey)) {
				if(flow.getMaaSPackageUsage().get(pacakgeId)==null) {
					volume = 0;
				}else {
					volume = flow.getMaaSPackageUsage().get(pacakgeId);
				}
			}else if(MaaSUtil.ifMaaSTransitLinesDiscountVariableDetails(skey)||MaaSUtil.ifMaaSFareLinkClusterVariableDetails(skey)) {
				Set<String> fls = sue.getTransitLineFareLinkMap().get(key); 
				for(String ff:fls) {
					FareLink f = new FareLink(ff);
					double flvolume=0;
					for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
						if(timeFl.getValue().get(pacakgeId)!=null && timeFl.getValue().get(pacakgeId).get(f.toString())!=null) {
							flvolume+=timeFl.getValue().get(pacakgeId).get(f.toString());//subtract from gradient
						}
					}
					double fare = fareCalculators.get(f.getMode()).getMinFare(f.getTransitRoute(), f.getTransitLine(), f.getBoardingStopFacility(), f.getAlightingStopFacility());
				volume+=-1*flvolume*fare/v.getValue();
				}
			}
			double vol = volume;
			if(operatorGradient.containsKey(operatorId))operatorGradient.get(operatorId).compute(skey, (k,vv)->vv+vol);
			//However, the first case is not true for the rest of the gradient element. As the farelink volume or maas package user volume gradient will always be dependent on the network and can be non zero.
			// the rest of the gradient component should be similar to the volume calculation. 
			final String kkk = key;
			//first maas package choice gradient
			sue.getPacakgeUserGradient().entrySet().forEach(pu->{
				if(!pu.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
					String o = packages.getMassPackages().get(pu.getKey()).getOperatorId();
					double cost = packages.getMassPackages().get(pu.getKey()).getPackageCost();
					if(operatorGradient.containsKey(o)) {
						operatorGradient.get(o).compute(skey,(k,vv)->vv+pu.getValue().get(kkk)*cost);
					}
				}
			});
			//Now fareLink gradients
			for(Entry<String,Map<String,Map<String,Map<String,Double>>>> timeFareGrad:sue.getFareLinkGradient().entrySet()){
				for(Entry<String,Map<String,Map<String,Double>>> maasFareGrad:timeFareGrad.getValue().entrySet()){
					String maasOperatorId = null;
					if(!maasFareGrad.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
						maasOperatorId = packages.getMassPackages().get(maasFareGrad.getKey()).getOperatorId();
					}
					for(Entry<String,Map<String,Double>> fareGrad:maasFareGrad.getValue().entrySet()){
						FareLink fl = new FareLink(fareGrad.getKey());
						String fareLinkOpId = packages.getOperatorId(fl); 
						double discount = 0;
						double rr = 1;
						double fullFare = fareCalculators.get(fl.getMode()).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
						if(maasOperatorId!=null) {
							discount = packages.getMassPackages().get(maasFareGrad.getKey()).getDiscountForFareLink(fl);
							rr = packages.getMassPackages().get(maasFareGrad.getKey()).getOperatorReimburesementRatio().get(fareLinkOpId);
							//for maaspackage operator
							//each element should be -1*reimbursementRatio*discount*gradient
							if(discount>0 && operators.containsKey(maasOperatorId)) {
								double grad = fareGrad.getValue().get(key)*-1*rr*discount;
								operatorGradient.get(maasOperatorId).compute(skey, (k,vv)->(v==null)?grad:vv+grad);
							}
						}
						//for fare link operator
						//each element should be (fare-(1-reimbursementRatio)*discount)*gradient
						if(fareLinkOpId!=null && operators.containsKey(fareLinkOpId)) {
							double grad = 0;
							if(discount>0) {
								grad = fareGrad.getValue().get(key)*(fullFare-discount*(1-rr));
							}else {
								grad = fareGrad.getValue().get(key)*fullFare;
							}
							final double g =grad;
							operatorGradient.get(fareLinkOpId).compute(skey, (k,vv)->(v==null)?g:vv+g);
						}

					}
				}
			}
				
			
		});
		
				
		
		return operatorGradient;
	}
	
	public static Map<String,Map<String,Double>> calcTotalSystemTravelTimeGradient(PersonPlanSueModel sue, SUEModelOutput flow, Map<String,Double> variables, Map<String,Map<String,VariableDetails>> operators,Double vot_car, Double vot_transit, Double vot_wait, Double vom){
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		Map<String,Map<String,Double>>operatorSmGradient = new HashMap<>();
		operators.keySet().forEach(o->operatorGradient.put(o, new HashMap<>()));
		operators.keySet().forEach(o->operatorSmGradient.put(o, new HashMap<>()));
		operators.entrySet().stream().forEach(o->{
			for(Entry<String,VariableDetails> v:o.getValue().entrySet()){
			//o.getValue().entrySet().parallelStream().forEach(v->{
				String skey = v.getKey();
				String key = MaaSUtil.retrieveName(skey);
				if(key == "") key =skey;
				double totalSystemTTGrad = 0;
				// only link flow 
				double tslttGrad = 0;
				for(Entry<String,Map<Id<Link>,Map<String,Double>>> net : sue.getLinkTravelTimeGradient().entrySet()) {
					for(Entry<Id<Link>,Map<String,Double>> link:net.getValue().entrySet()) {
						if(sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey())==null) {
							System.out.println("Debug!!!");
						}
						double lVol = flow.getLinkVolume().get(net.getKey()).get(link.getKey());
						double ttGrad = sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey()).get(key);
						double lGrad = sue.getLinkGradient().get(net.getKey()).get(link.getKey()).get(key);
						double lTT = flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
						tslttGrad+=flow.getLinkVolume().get(net.getKey()).get(link.getKey())*sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey()).get(key)+
								sue.getLinkGradient().get(net.getKey()).get(link.getKey()).get(key)*flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
					}
				}
				//only transit direct link flow 
				double tstdlttGrad = 0;
				double tsttlttGrad = 0;
				for(Entry<String,Map<Id<TransitLink>,Double>> trLinks:flow.getLinkTransitVolume().entrySet()) {
					for(Entry<Id<TransitLink>,Double>link:trLinks.getValue().entrySet()) {
						if(flow.getTransitDirectLinkTT().get(trLinks.getKey()).containsKey(link.getKey())){
							tstdlttGrad+=link.getValue()*sue.getTrLinkTravelTimeGradient().get(trLinks.getKey()).get(link.getKey()).get(key)+
									sue.getTrLinkGradient().get(trLinks.getKey()).get(link.getKey()).get(key)*flow.getTransitDirectLinkTT().get(trLinks.getKey()).get(link.getKey());
						}else {
							tsttlttGrad+=link.getValue()*sue.getTrLinkTravelTimeGradient().get(trLinks.getKey()).get(link.getKey()).get(key)+
									sue.getTrLinkGradient().get(trLinks.getKey()).get(link.getKey()).get(key)*flow.getTransitTransferLinkTT().get(trLinks.getKey()).get(link.getKey());
						}
					}
				}
				totalSystemTTGrad = tslttGrad*vot_car/vom+tstdlttGrad*vot_transit/vom+tsttlttGrad*vot_wait/vom;
				operatorGradient.get(o.getKey()).put(skey, totalSystemTTGrad);
				operatorSmGradient.get(o.getKey()).put(key, totalSystemTTGrad);
			//});
		}
			if(o.getValue().size()!=operatorGradient.get(o.getKey()).size()) {
				System.out.println("Debug!!!");
			}
		});
		System.out.println();
		return operatorGradient;
	}
	
	public static void measureAverreageTLWaitingTime(PersonPlanSueModel model, SUEModelOutput flow, String fileLoc) {
		Map<Id<TransitLine>,Integer> occurance = new HashMap<>();
		Map<Id<TransitLine>,Double> waitTime = new HashMap<>();
		for(Entry<String,Map<Id<TransitLink>,Double>>timeMap:flow.getTransitTransferLinkTT().entrySet()) {
			for(Entry<Id<TransitLink>,Double> waitTimeMap:timeMap.getValue().entrySet()) {
				CNLTransitTransferLink link  = ((CNLTransitTransferLink)model.getTransitLinks().get(timeMap.getKey()).get(waitTimeMap.getKey()));
				if(link.getNextdLink()!=null) {
					Id<TransitLine> lineId = Id.create(link.getNextdLink().getLineId(),TransitLine.class);
					occurance.compute(lineId,(k,v)->v==null?1:v+1);
					waitTime.compute(lineId, (k,v)->v==null?waitTimeMap.getValue():(v*(occurance.get(lineId)-1)+waitTimeMap.getValue())/occurance.get(lineId));
				}
			}
		}
		try {
			FileWriter fw = new FileWriter(new File(fileLoc));
			for(Entry<Id<TransitLine>,Double> e:waitTime.entrySet()) {
				fw.append(e.getKey().toString()+","+e.getValue()+"\n");
			}
			fw.flush();
			fw.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	public static Map<Id<TransitLine>,Double> readSimpleMap(String fileLoc){
		Map<Id<TransitLine>,Double> map = new HashMap<>();
		try {
			BufferedReader bf = new BufferedReader(new FileReader(new File(fileLoc)));
			String line = null;
			while((line = bf.readLine())!=null) {
				map.put(Id.create(line.split(",")[0], TransitLine.class), Double.parseDouble(line.split(",")[1]));
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		return map;
	}
}
