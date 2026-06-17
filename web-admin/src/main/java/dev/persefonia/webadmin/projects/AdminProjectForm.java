package dev.persefonia.webadmin.projects;

import java.util.ArrayList;
import java.util.List;

public final class AdminProjectForm {
    private String status = "EXPERIMENT";
    private String visibility = "PRIVATE";
    private boolean featured;
    private String sortOrder = "";
    private boolean trEnabled;
    private String trSlug = "";
    private String trTitle = "";
    private String trSummary = "";
    private String trProblem = "";
    private String trContext = "";
    private String trRole = "";
    private String trApproach = "";
    private String trArchitecture = "";
    private String trDecisions = "";
    private String trTradeoffs = "";
    private String trResult = "";
    private String trLessons = "";
    private String trFuture = "";
    private boolean enEnabled;
    private String enSlug = "";
    private String enTitle = "";
    private String enSummary = "";
    private String enProblem = "";
    private String enContext = "";
    private String enRole = "";
    private String enApproach = "";
    private String enArchitecture = "";
    private String enDecisions = "";
    private String enTradeoffs = "";
    private String enResult = "";
    private String enLessons = "";
    private String enFuture = "";
    private String technologies = "";
    private String links = "";
    private List<String> tagIds = new ArrayList<>();

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status == null ? "" : status; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility == null ? "" : visibility; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder == null ? "" : sortOrder; }
    public boolean isTrEnabled() { return trEnabled; }
    public void setTrEnabled(boolean trEnabled) { this.trEnabled = trEnabled; }
    public String getTrSlug() { return trSlug; }
    public void setTrSlug(String trSlug) { this.trSlug = trSlug == null ? "" : trSlug; }
    public String getTrTitle() { return trTitle; }
    public void setTrTitle(String trTitle) { this.trTitle = trTitle == null ? "" : trTitle; }
    public String getTrSummary() { return trSummary; }
    public void setTrSummary(String trSummary) { this.trSummary = trSummary == null ? "" : trSummary; }
    public String getTrProblem() { return trProblem; }
    public void setTrProblem(String trProblem) { this.trProblem = trProblem == null ? "" : trProblem; }
    public String getTrContext() { return trContext; }
    public void setTrContext(String trContext) { this.trContext = trContext == null ? "" : trContext; }
    public String getTrRole() { return trRole; }
    public void setTrRole(String trRole) { this.trRole = trRole == null ? "" : trRole; }
    public String getTrApproach() { return trApproach; }
    public void setTrApproach(String trApproach) { this.trApproach = trApproach == null ? "" : trApproach; }
    public String getTrArchitecture() { return trArchitecture; }
    public void setTrArchitecture(String trArchitecture) { this.trArchitecture = trArchitecture == null ? "" : trArchitecture; }
    public String getTrDecisions() { return trDecisions; }
    public void setTrDecisions(String trDecisions) { this.trDecisions = trDecisions == null ? "" : trDecisions; }
    public String getTrTradeoffs() { return trTradeoffs; }
    public void setTrTradeoffs(String trTradeoffs) { this.trTradeoffs = trTradeoffs == null ? "" : trTradeoffs; }
    public String getTrResult() { return trResult; }
    public void setTrResult(String trResult) { this.trResult = trResult == null ? "" : trResult; }
    public String getTrLessons() { return trLessons; }
    public void setTrLessons(String trLessons) { this.trLessons = trLessons == null ? "" : trLessons; }
    public String getTrFuture() { return trFuture; }
    public void setTrFuture(String trFuture) { this.trFuture = trFuture == null ? "" : trFuture; }
    public boolean isEnEnabled() { return enEnabled; }
    public void setEnEnabled(boolean enEnabled) { this.enEnabled = enEnabled; }
    public String getEnSlug() { return enSlug; }
    public void setEnSlug(String enSlug) { this.enSlug = enSlug == null ? "" : enSlug; }
    public String getEnTitle() { return enTitle; }
    public void setEnTitle(String enTitle) { this.enTitle = enTitle == null ? "" : enTitle; }
    public String getEnSummary() { return enSummary; }
    public void setEnSummary(String enSummary) { this.enSummary = enSummary == null ? "" : enSummary; }
    public String getEnProblem() { return enProblem; }
    public void setEnProblem(String enProblem) { this.enProblem = enProblem == null ? "" : enProblem; }
    public String getEnContext() { return enContext; }
    public void setEnContext(String enContext) { this.enContext = enContext == null ? "" : enContext; }
    public String getEnRole() { return enRole; }
    public void setEnRole(String enRole) { this.enRole = enRole == null ? "" : enRole; }
    public String getEnApproach() { return enApproach; }
    public void setEnApproach(String enApproach) { this.enApproach = enApproach == null ? "" : enApproach; }
    public String getEnArchitecture() { return enArchitecture; }
    public void setEnArchitecture(String enArchitecture) { this.enArchitecture = enArchitecture == null ? "" : enArchitecture; }
    public String getEnDecisions() { return enDecisions; }
    public void setEnDecisions(String enDecisions) { this.enDecisions = enDecisions == null ? "" : enDecisions; }
    public String getEnTradeoffs() { return enTradeoffs; }
    public void setEnTradeoffs(String enTradeoffs) { this.enTradeoffs = enTradeoffs == null ? "" : enTradeoffs; }
    public String getEnResult() { return enResult; }
    public void setEnResult(String enResult) { this.enResult = enResult == null ? "" : enResult; }
    public String getEnLessons() { return enLessons; }
    public void setEnLessons(String enLessons) { this.enLessons = enLessons == null ? "" : enLessons; }
    public String getEnFuture() { return enFuture; }
    public void setEnFuture(String enFuture) { this.enFuture = enFuture == null ? "" : enFuture; }
    public String getTechnologies() { return technologies; }
    public void setTechnologies(String technologies) { this.technologies = technologies == null ? "" : technologies; }
    public String getLinks() { return links; }
    public void setLinks(String links) { this.links = links == null ? "" : links; }
    public List<String> getTagIds() { return tagIds; }
    public void setTagIds(List<String> tagIds) { this.tagIds = tagIds == null ? new ArrayList<>() : new ArrayList<>(tagIds); }
}
