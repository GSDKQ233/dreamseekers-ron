package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.survival.SurvivalResult;
import java.util.LinkedHashMap;

public class RTSPlayerScores {
    public LinkedHashMap<RTSPlayerScoresEnum, Integer> scoreList = new LinkedHashMap<>();

    public RTSPlayerScores() {
        scoreList.put(RTSPlayerScoresEnum.TOTAL_BUILDINGS_CONSTRUCTED, 0);
        scoreList.put(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED, 0);
        scoreList.put(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED, 0);
        scoreList.put(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED, 0);
        scoreList.put(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED, 0);
        scoreList.put(RTSPlayerScoresEnum.FOOD_HARVESTED, 0);
        scoreList.put(RTSPlayerScoresEnum.WOOD_HARVESTED, 0);
        scoreList.put(RTSPlayerScoresEnum.ORES_HARVESTED, 0);
    }

    public LinkedHashMap<RTSPlayerScoresEnum, Integer> getScoreList() {
        return scoreList;
    }

    public void addToScore(RTSPlayerScoresEnum scoresEnum) {
        addToScore(scoresEnum, 1);
    }

    public void addToScore(RTSPlayerScoresEnum scoresEnum, int amount) {
        if (SurvivalResult.isAfterMatch()) return;
        int score = scoreList.get(scoresEnum) + amount;
        scoreList.put(scoresEnum, score);
    }

    /** Native and addon passive-income writers share the match-end scoring gate. */
    public void addHarvestScore(RTSPlayerScoresEnum key, long amount) {
        if (amount <= 0 || SurvivalResult.isAfterMatch()) return;
        long old = Math.max(0, scoreList.getOrDefault(key, 0));
        scoreList.put(key, (int) (old + Math.min(Integer.MAX_VALUE - old, amount)));
    }

    public static String getScoreInitials(RTSPlayerScoresEnum scoresEnum) {
        String[] words = scoresEnum.toString().split("_");
        StringBuilder initials = new StringBuilder();

        for (String word : words) {
            initials.append(word.charAt(0));
        }
        return initials.toString();
    }

    public int[] getScoreListAsArray() {
        RTSPlayerScoresEnum[] values = RTSPlayerScoresEnum.values();
        int[] result = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            result[i] = scoreList.get(values[i]);
        }
        return result;
    }

    public void setScoreListFromArray(int[] arrayList) {
        int i = 0;
        for (RTSPlayerScoresEnum scoresEnum : RTSPlayerScoresEnum.values()) {
            scoreList.put(scoresEnum, arrayList[i]);
            i++;
        }
    }

    // May be redundant
    /* public int getScore(RTSPlayerScoresEnum scoreEnum) {
        return scoreList.get(scoreEnum);
    } */
}
