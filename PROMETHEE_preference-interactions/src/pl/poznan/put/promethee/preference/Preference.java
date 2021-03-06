package pl.poznan.put.promethee.preference;

import java.util.Map;
import java.util.LinkedHashMap;

import org.xmcda.Threshold;

import pl.poznan.put.promethee.exceptions.InvalidZFunctionParamException;
import pl.poznan.put.promethee.exceptions.NullThresholdException;
import pl.poznan.put.promethee.exceptions.PositiveNetBalanceException;
import pl.poznan.put.promethee.exceptions.WrongPreferenceDirectionException;
import pl.poznan.put.promethee.xmcda.InputsHandler;
import pl.poznan.put.promethee.xmcda.InputsHandler.ComparisonWithParam;
import pl.poznan.put.promethee.xmcda.InputsHandler.Inputs;
import pl.poznan.put.promethee.xmcda.InputsHandler.ZFunctionParam;

public class Preference {

	public static Map<String, Map<String, Double>> calculatePreferences(InputsHandler.Inputs inputs,
			Map<String, Map<String, Map<String, Double>>> partialPreferences) throws WrongPreferenceDirectionException,
			NullThresholdException, InvalidZFunctionParamException, PositiveNetBalanceException {
		checkNetBalance(inputs);
		calcPartialPreferences(inputs, partialPreferences);
		Map<String, Map<String, Double>> preferences = new LinkedHashMap<>();
		if (inputs.comparisonWith == ComparisonWithParam.ALTERNATIVES) {
			for (String a : inputs.alternatives_ids) {
				for (String b : inputs.alternatives_ids) {
					preferences.putIfAbsent(a, new LinkedHashMap<>());
					preferences.get(a).put(b, calcTotalPreference(a, b, inputs, partialPreferences));
				}
			}
		} else {
			for (String a : inputs.alternatives_ids) {
				for (String b : inputs.profiles_ids) {
					preferences.putIfAbsent(a, new LinkedHashMap<>());
					preferences.get(a).put(b, calcTotalPreference(a, b, inputs, partialPreferences));
					preferences.putIfAbsent(b, new LinkedHashMap<>());
					preferences.get(b).put(a, calcTotalPreference(b, a, inputs, partialPreferences));
				}
			}
			for (String a : inputs.profiles_ids) {
				for (String b : inputs.profiles_ids) {
					preferences.putIfAbsent(a, new LinkedHashMap<>());
					preferences.get(a).put(b, calcTotalPreference(a, b, inputs, partialPreferences));
				}
			}
		}
		return preferences;
	}

	/**
	 * @param direction
	 *            direction of function on criterion ('MIN' or 'MAX')
	 * @param ga
	 *            evaluation of alternative on specified criterion
	 * @param gb
	 *            evaluation of alternative on specified criterion
	 * @return difference between evaluations
	 * @throws WrongPreferenceDirectionException
	 */
	private static Double calcDifferenceBetweenEvaluations(String direction, Double ga, Double gb)
			throws WrongPreferenceDirectionException {
		Double differenceBetweenEvaluations = 0.0;
		if (direction.equals("MAX")) {
			differenceBetweenEvaluations = ga - gb;
		} else if (direction.equals("MIN")) {
			differenceBetweenEvaluations = gb - ga;
		} else {
			throw new WrongPreferenceDirectionException();
		}
		return differenceBetweenEvaluations;
	}

	/**
	 * @param direction
	 * @param ga
	 * @param gb
	 * @param threshold
	 * @return calculated final threshold value (get constant if exist or
	 *         calculate value if defined as linear)
	 * @throws WrongPreferenceDirectionException
	 */
	private static Double calcThreshold(String direction, Double ga, Double gb, Threshold<Double> threshold)
			throws WrongPreferenceDirectionException {
		if (threshold == null) {
			return null;
		}
		Double thresholdValue = 0.0;
		if (threshold.isConstant()) {
			thresholdValue = threshold.getConstant().getValue();
		} else {
			Double baseEvaluation = 0.0;
			if (direction.equals("MAX")) {
				baseEvaluation = ga > gb ? gb : ga;
			} else if (direction.equals("MIN")) {
				baseEvaluation = ga > gb ? ga : gb;
			} else {
				throw new WrongPreferenceDirectionException();
			}
			Double slope = threshold.getSlope().getValue();
			Double intercept = threshold.getIntercept().getValue();
			thresholdValue = slope * baseEvaluation + intercept;
		}
		return thresholdValue;
	}

	private static Double calcPreferenceOnOneCriterion(Double ga, Double gb, String direction, Integer functionNumber,
			Threshold<Double> preferenceThreshold, Threshold<Double> indifferenceThreshold,
			Threshold<Double> sigmaThreshold) throws WrongPreferenceDirectionException, NullThresholdException {
		GeneralisedCriteria generalisedCriteria = new GeneralisedCriteria();

		Double diff = calcDifferenceBetweenEvaluations(direction, ga, gb);
		Double p = calcThreshold(direction, ga, gb, preferenceThreshold);
		Double q = calcThreshold(direction, ga, gb, indifferenceThreshold);
		Double s = calcThreshold(direction, ga, gb, sigmaThreshold);

		Double preference = generalisedCriteria.calculate(functionNumber, diff, p, q, s);
		return preference;
	}

	/**
	 * @param inputs
	 * @return matrix of preferences - all alternatives with all
	 *         alternatives(profiles) on all criteria
	 * @throws WrongPreferenceDirectionException
	 * @throws NullThresholdException
	 */
	private static void calcPartialPreferences(Inputs inputs,
			Map<String, Map<String, Map<String, Double>>> partialPreferences)
			throws WrongPreferenceDirectionException, NullThresholdException {
		if (inputs.comparisonWith == ComparisonWithParam.ALTERNATIVES) {
			for (String a : inputs.alternatives_ids) {
				for (String b : inputs.alternatives_ids) {
					for (String c : inputs.criteria_ids) {
						partialPreferences.putIfAbsent(a, new LinkedHashMap<>());
						partialPreferences.get(a).putIfAbsent(b, new LinkedHashMap<>());
						partialPreferences.get(a).get(b).put(c, calcPreferenceOnOneCriterion(
								inputs.performanceTable.get(a).get(c).doubleValue(),
								inputs.performanceTable.get(b).get(c).doubleValue(), inputs.preferenceDirections.get(c),
								inputs.generalisedCriteria.get(c).intValue(), inputs.preferenceThresholds.get(c),
								inputs.indifferenceThresholds.get(c), inputs.sigmaThresholds.get(c)));
					}
				}
			}
		} else {
			for (String a : inputs.alternatives_ids) {
				for (String b : inputs.profiles_ids) {
					for (String c : inputs.criteria_ids) {
						partialPreferences.putIfAbsent(a, new LinkedHashMap<>());
						partialPreferences.get(a).putIfAbsent(b, new LinkedHashMap<>());
						partialPreferences.get(a).get(b).put(c,
								calcPreferenceOnOneCriterion(inputs.performanceTable.get(a).get(c).doubleValue(),
										inputs.profilesPerformanceTable.get(b).get(c).doubleValue(),
										inputs.preferenceDirections.get(c),
										inputs.generalisedCriteria.get(c).intValue(),
										inputs.preferenceThresholds.get(c), inputs.indifferenceThresholds.get(c),
										inputs.sigmaThresholds.get(c)));
						partialPreferences.putIfAbsent(b, new LinkedHashMap<>());
						partialPreferences.get(b).putIfAbsent(a, new LinkedHashMap<>());
						partialPreferences.get(b).get(a).put(c, calcPreferenceOnOneCriterion(
								inputs.profilesPerformanceTable.get(b).get(c).doubleValue(),
								inputs.performanceTable.get(a).get(c).doubleValue(), inputs.preferenceDirections.get(c),
								inputs.generalisedCriteria.get(c).intValue(), inputs.preferenceThresholds.get(c),
								inputs.indifferenceThresholds.get(c), inputs.sigmaThresholds.get(c)));
					}
				}
			}
			for (String a : inputs.profiles_ids) {
				for (String b : inputs.profiles_ids) {
					for (String c : inputs.criteria_ids) {
						partialPreferences.putIfAbsent(a, new LinkedHashMap<>());
						partialPreferences.get(a).putIfAbsent(b, new LinkedHashMap<>());
						partialPreferences.get(a).get(b).put(c, calcPreferenceOnOneCriterion(
								inputs.profilesPerformanceTable.get(a).get(c).doubleValue(),
								inputs.profilesPerformanceTable.get(b).get(c).doubleValue(),
								inputs.preferenceDirections.get(c), inputs.generalisedCriteria.get(c).intValue(),
								inputs.preferenceThresholds.get(c), inputs.indifferenceThresholds.get(c),
								inputs.sigmaThresholds.get(c)));
					}
				}
			}
		}
	}

	private static Double calcTotalPreference(String alternative1, String alternative2, Inputs inputs,
			Map<String, Map<String, Map<String, Double>>> partialPreferences) throws InvalidZFunctionParamException {
		Double preference = 0.0;
		Double totalWeight = 0.0;
		for (String criterion : inputs.criteria_ids) {
			Double weight = inputs.weights.get(criterion);
			totalWeight += weight;
			preference += (partialPreferences.get(alternative1).get(alternative2).get(criterion) * weight);
		}
		Double interactionsSum = 0.0;
		Double antagonisticSum = 0.0;
		for (String rowCriterion : inputs.strengtheningEffect.keySet()) {
			for (String columnCriterion : inputs.strengtheningEffect.get(rowCriterion).keySet()) {
				Double ci = partialPreferences.get(alternative1).get(alternative2).get(rowCriterion);
				Double cj = partialPreferences.get(alternative1).get(alternative2).get(columnCriterion);
				interactionsSum += calcZFunction(inputs.zFunction, ci, cj)
						* inputs.strengtheningEffect.get(rowCriterion).get(columnCriterion).doubleValue();
			}
		}
		for (String rowCriterion : inputs.weakeningEffect.keySet()) {
			for (String columnCriterion : inputs.weakeningEffect.get(rowCriterion).keySet()) {
				Double ci = partialPreferences.get(alternative1).get(alternative2).get(rowCriterion);
				Double cj = partialPreferences.get(alternative1).get(alternative2).get(columnCriterion);
				interactionsSum += calcZFunction(inputs.zFunction, ci, cj)
						* inputs.weakeningEffect.get(rowCriterion).get(columnCriterion).doubleValue();
			}
		}
		for (String rowCriterion : inputs.antagonisticEffect.keySet()) {
			for (String columnCriterion : inputs.antagonisticEffect.get(rowCriterion).keySet()) {
				Double ci = partialPreferences.get(alternative1).get(alternative2).get(rowCriterion);
				Double cj = partialPreferences.get(alternative2).get(alternative1).get(columnCriterion);
				antagonisticSum += calcZFunction(inputs.zFunction, ci, cj)
						* inputs.antagonisticEffect.get(rowCriterion).get(columnCriterion).doubleValue();
			}
		}

		Double K = totalWeight + interactionsSum - antagonisticSum;
		preference = (preference + interactionsSum - antagonisticSum) / K;
		return preference;
	}

	private static Double calcZFunction(ZFunctionParam zFunction, Double x, Double y)
			throws InvalidZFunctionParamException {
		if (zFunction.equals(ZFunctionParam.MULTIPLICATION)) {
			return x * y;
		}
		if (zFunction.equals(ZFunctionParam.MINIMUM)) {
			return Math.min(x, y);
		}
		throw new InvalidZFunctionParamException();
	}

	private static void checkNetBalance(Inputs inputs) throws PositiveNetBalanceException {
		Map<String, Double> criteriaWeakSum = new LinkedHashMap<>();

		for (String rowCriterion : inputs.weakeningEffect.keySet()) {
			for (String columnCriterion : inputs.weakeningEffect.get(rowCriterion).keySet()) {
				criteriaWeakSum.putIfAbsent(rowCriterion, 0.0);
				criteriaWeakSum.put(rowCriterion, criteriaWeakSum.get(rowCriterion)
						+ Math.abs(inputs.weakeningEffect.get(rowCriterion).get(columnCriterion).doubleValue()));
			}
		}
		for (String rowCriterion : inputs.weakeningEffectReverse.keySet()) {
			for (String columnCriterion : inputs.weakeningEffectReverse.get(rowCriterion).keySet()) {
				criteriaWeakSum.putIfAbsent(rowCriterion, 0.0);
				criteriaWeakSum.put(rowCriterion, criteriaWeakSum.get(rowCriterion)
						+ Math.abs(inputs.weakeningEffectReverse.get(rowCriterion).get(columnCriterion).doubleValue()));
			}
		}
		for (String rowCriterion : inputs.antagonisticEffect.keySet()) {
			for (String columnCriterion : inputs.antagonisticEffect.get(rowCriterion).keySet()) {
				criteriaWeakSum.putIfAbsent(rowCriterion, 0.0);
				criteriaWeakSum.put(rowCriterion, criteriaWeakSum.get(rowCriterion)
						+ Math.abs(inputs.antagonisticEffect.get(rowCriterion).get(columnCriterion).doubleValue()));
			}
		}
		for (String criterion : criteriaWeakSum.keySet()) {
			if (inputs.weights.get(criterion) - criteriaWeakSum.get(criterion) <= 0) {
				throw new PositiveNetBalanceException(criterion);
			}
		}
	}
}
