# re-mount-module-browser

[Re-mount](https://github.com/district0x/d0x-INFRA/blob/master/re-mount.md) module browser.

Index and explore various aspects of your clojurescript projects.

### Usage
```
lein uberjar
java -jar target/re-mount-module-browser.jar /home/user/base-projects-folder
```
and then open http://localhost:3000

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

#### Maven project dependecies
<img src="/docs/dependencies.png?raw=true"/>

#### Namespaces and mount state dependencies 
<img src="/docs/namespaces.png?raw=true"/>

#### Mount state dependencies 
<img src="/docs/mount.png?raw=true"/>

#### Re-frame events, subscriptions, effects and coeffects
<img src="/docs/reframe.png?raw=true"/>

#### Specs
<img src="/docs/specs.png?raw=true"/>

#### Clicking everywhere takes you to exact file/line
<img src="/docs/code.png?raw=true"/>

### Similar apps

For similar approach but around smart contracts analisys check [Smart View](https://github.com/jpmonettas/smart-view)

### Roadmap
- Index npm dependencies
- Index more code facts to be able to create lint tools

