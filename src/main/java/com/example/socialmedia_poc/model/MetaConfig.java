package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class MetaConfig {
    @JsonProperty("intensity_range")
    private List<Integer> intensityRange;
    private String pacing;
    @JsonProperty("word_count_range")
    private List<Integer> wordCountRange;
    private List<String> triggers;
    private List<String> language;
    @JsonProperty("dwell_logic")
    private Map<String, String> dwellLogic;
    @JsonProperty("vocabulary_weight")
    private Map<String, Double> vocabularyWeight;
    @JsonProperty("dopamine_keywords")
    private List<String> dopamineKeywords;
    @JsonProperty("tone_guide")
    private String toneGuide;

    public List<Integer> getIntensityRange() {
        return intensityRange;
    }

    public void setIntensityRange(List<Integer> intensityRange) {
        this.intensityRange = intensityRange;
    }

    public String getPacing() {
        return pacing;
    }

    public void setPacing(String pacing) {
        this.pacing = pacing;
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<String> triggers) {
        this.triggers = triggers;
    }

    public Map<String, String> getDwellLogic() {
        return dwellLogic;
    }

    public void setDwellLogic(Map<String, String> dwellLogic) {
        this.dwellLogic = dwellLogic;
    }

    public Map<String, Double> getVocabularyWeight() {
        return vocabularyWeight;
    }

    public void setVocabularyWeight(Map<String, Double> vocabularyWeight) {
        this.vocabularyWeight = vocabularyWeight;
    }

    public List<String> getLanguage() {
        return language;
    }

    public void setLanguage(List<String> language) {
        this.language = language;
    }

    public List<Integer> getWordCountRange() {
        return wordCountRange;
    }

    public void setWordCountRange(List<Integer> wordCountRange) {
        this.wordCountRange = wordCountRange;
    }

    public List<String> getDopamineKeywords() {
        return dopamineKeywords;
    }

    public void setDopamineKeywords(List<String> dopamineKeywords) {
        this.dopamineKeywords = dopamineKeywords;
    }

    public String getToneGuide() {
        return toneGuide;
    }

    public void setToneGuide(String toneGuide) {
        this.toneGuide = toneGuide;
    }
}
