package com.github.t1.openapi.ui.components;

import com.github.t1.htmljava.AbstractElement;
import com.github.t1.htmljava.Element;

import java.util.function.Consumer;

import static com.github.t1.htmljava.HtmlBasics.span;

/// A toggle toggle that emits a `toggle` CustomEvent on selection change.
public class Toggle extends AbstractElement<Toggle> {

    public static Toggle toggle(String name) {return new Toggle(name);}

    private Toggle(String name) {
        super("div");
        classes("toggle");
        attr("data-toggle", name);
        attr("tabindex", "0");
    }

    public Toggle option(String value) {return option(value, value, false, o -> {});}

    public Toggle option(String value, String label) {return option(value, label, false, o -> {});}

    public Toggle option(String value, Consumer<Element> customizer) {return option(value, value, false, customizer);}

    public Toggle option(String value, String label, Consumer<Element> customizer) {return option(value, label, false, customizer);}

    public Toggle activeOption(String value) {return option(value, value, true, o -> {});}

    public Toggle activeOption(String value, String label) {return option(value, label, true, o -> {});}

    public Toggle activeOption(String value, Consumer<Element> customizer) {return option(value, value, true, customizer);}

    public Toggle activeOption(String value, String label, Consumer<Element> customizer) {return option(value, label, true, customizer);}

    private Toggle option(String value, String label, boolean active, Consumer<Element> customizer) {
        var option = span(label).attr("data-toggle-value", value);
        if (active) option.classes("is-active");
        customizer.accept(option);
        content(option);
        return this;
    }

    public Toggle activate(String value) {
        contentStream()
                .filter(AbstractElement.class::isInstance)
                .map(AbstractElement.class::cast)
                .filter(el -> el.hasAttribute("data-toggle-value"))
                .forEach(el -> {
                    if (el.hasAttribute("data-toggle-value", value)) el.classes("is-active");
                    else el.notClasses("is-active");
                });
        return this;
    }

    public Toggle persistAs(String key) {
        attr("data-persist", key);
        return this;
    }

    public static String css() {return CSS;}

    public static String js() {return JS;}

    private static final String CSS = """
            .toggle {
                display: inline-flex;
                gap: 1px;
                background: var(--bulma-scheme-main-ter);
                border-radius: 6px;
                padding: 2px;
            }
            .toggle > span {
                padding: 5px 14px;
                font-size: 0.75rem;
                color: var(--bulma-text-weak);
                border-radius: 5px;
                cursor: pointer;
                transition: all 0.15s;
                user-select: none;
            }
            .toggle > span.is-active {
                background: var(--bulma-scheme-main);
                color: var(--bulma-text-strong);
                font-weight: 500;
                box-shadow: 0 1px 2px rgba(0,0,0,0.06);
            }
            """;

    private static final String JS = """
            document.addEventListener('DOMContentLoaded', function() {
                function initToggle(container) {
                    var values = Array.from(container.querySelectorAll('[data-toggle-value]:not([data-overflow])')).map(function(el) {
                        return el.getAttribute('data-toggle-value');
                    });
                    function select(value) {
                        var current = container.querySelector('.is-active');
                        if (current && current.getAttribute('data-toggle-value') === value) return;
                        container.querySelectorAll('[data-toggle-value]').forEach(function(b) {
                            b.classList.remove('is-active');
                        });
                        container.querySelector('[data-toggle-value=' + value + ']').classList.add('is-active');
                        var persistKey = container.getAttribute('data-persist');
                        if (persistKey) localStorage.setItem(persistKey, value);
                        container.dispatchEvent(new CustomEvent('toggle', { detail: { value: value } }));
                    }
                    container.querySelectorAll('[data-toggle-value]').forEach(function(btn) {
                        btn.addEventListener('click', function() {
                            select(btn.getAttribute('data-toggle-value'));
                        });
                    });
                    container.addEventListener('keydown', function(e) {
                        var current = container.querySelector('.is-active').getAttribute('data-toggle-value');
                        var idx = values.indexOf(current);
                        if (e.key === 'ArrowRight') {
                            e.preventDefault();
                            var next = Math.min(idx + 1, values.length - 1);
                            if (next !== idx) select(values[next]);
                        } else if (e.key === 'ArrowLeft') {
                            e.preventDefault();
                            var next = Math.max(idx - 1, 0);
                            if (next !== idx) select(values[next]);
                        } else if (e.key === 'Home') {
                            e.preventDefault();
                            select(values[0]);
                        } else if (e.key === 'End') {
                            e.preventDefault();
                            select(values[values.length - 1]);
                        }
                    });
                    container._select = select;
                }
                document.querySelectorAll('.toggle[data-toggle]').forEach(initToggle);
            });
            """;
}
