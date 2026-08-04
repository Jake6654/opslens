package com.opslens.service;

import java.util.List;

public class PullRequestBlockedException extends RuntimeException{

    private final List<String> blockers;

    public PullRequestBlockedException(List<String> blokcers){
        super("Pull request branch creation is blocked.");
        this.blockers = List.copyOf(blokcers);
    }

    public List<String> getBlockers(){
        return blockers;
    }
}
