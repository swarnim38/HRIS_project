package model;

import java.util.Objects;

public record Candidate(Scope.Key key, String name) {
    public Candidate {
        Objects.requireNonNull(key);
        name = Scope.required(name, "candidate name");
    }
}
