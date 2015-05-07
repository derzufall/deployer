package com.github.t1.deployer.app.html.builder2;

public abstract class Component {
    public BuildContext write(Object target) {
        return new BuildContext(this, target);
    }

    public abstract void writeTo(BuildContext out);

    public void writeInlineTo(BuildContext out) {
        writeTo(out);
    }

    public boolean isMultiLine() {
        return false;
    }
}
