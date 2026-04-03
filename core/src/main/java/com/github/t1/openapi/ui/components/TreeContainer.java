package com.github.t1.openapi.ui.components;

import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;

import java.util.function.Consumer;

/// Shared API between {@link Tree} and {@link Tree.Node} for adding items and subtrees.
public interface TreeContainer {
    TreeContainer item(String label);

    TreeContainer item(Renderable label);

    TreeContainer item(Renderable label, Consumer<Element> customizer);

    TreeContainer node(String label, Consumer<Tree.Node> children);

    TreeContainer node(Renderable label, Consumer<Tree.Node> children);
}
