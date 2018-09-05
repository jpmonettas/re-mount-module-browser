# re-mount-module-browser

[Re-mount](https://github.com/district0x/d0x-INFRA/blob/master/re-mount.md) module browser.

Static clojurescipt source code analyzer.
Index and explore various aspects of your clojurescript projects.

### Build
```
lein uberjar
```

### Usage

If you have a folder under which you clone all your organization's sub projects

```
java -jar target/re-mount-module-browser.jar /home/user/base-projects-folder
```
and then open http://localhost:3000

Note: You can also run it inside one of the projects folders but you will index much less information.

### Features

- Explore you projects transitive dependencies
- Understand dependencies needed for a namespace
- Explore namespace hierarchies
- Explore mount component hierarchies
- Explore re-frame events, subs, fxs and cofx of all your projects at once
- Explore all your projects specs at once
- Quickly jump into code

### How does it works?

- Walks over every project in base-projects-folder
- For every project.clj and .cljs file, transacts into a datascript db facts about :
    - Project name 
    - Project maven dependencies
    - Re frame features (subscriptions, events, effects, coeffects)
    - Mount state
    - Specs
- Serializes the datascript db and sends it to the browser where you can explore different aspects of your projects

### How does it looks?

After indexing finishes, this is the kind of information you can explore :

#### Maven project dependecies

You can select any of the indexed projects and see a graph of a dependency tree starting at
the selected project.

If you choose a project namespace, you will see painted in red all dependencies required by that namespace. 

<img src="/docs/dependencies.png?raw=true"/>

#### Namespaces and mount state dependencies 

You can select any project and namespace and see the entire namespace dependency tree.
Red nodes are namespaces containing mount state.

<img src="/docs/namespaces.png?raw=true"/>

#### Mount state dependencies 

Choose any project and namespace to see mount state hierarchy starting on that namespace.

<img src="/docs/mount.png?raw=true"/>

#### Re-frame events, subscriptions, effects and coeffects

You can explore re-frame events, subscriptions, effects and coeffects.
Each card represents a project, and each line inside is a re-frame feature grouped by ns.
Click on any item to see the code implementing it.

<img src="/docs/reframe.png?raw=true"/>

#### Specs

Same as with re-frame features you can explore your projects specs.

<img src="/docs/specs.png?raw=true"/>

#### Clicking everywhere takes you to exact file/line

<img src="/docs/code.png?raw=true"/>

### Similar apps

For similar approach but around smart contracts analisys check [Smart View](https://github.com/jpmonettas/smart-view)

### Roadmap
- Support clojure files
- Support boot projects
- Support deps.clj projects
- Index npm dependencies
- Index more code facts to be able to create lint tools

