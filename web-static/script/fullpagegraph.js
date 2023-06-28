const myDiagram = new go.Diagram(
    myDiagramDiv,
    {
      initialAutoScale: go.Diagram.Uniform,
      layout: new go.LayeredDigraphLayout(),
      "undoManager.isEnabled": false
    }
);

myDiagram.animationManager.initialAnimationStyle = go.AnimationManager.AnimateLocations;

function setForceDirectedLayout() {
  myDiagram.layout = new go.ForceDirectedLayout();
}
function setForceLayeredDigraphLayout() {
  myDiagram.layout = new go.LayeredDigraphLayout();
}
function setTreeLayout() {
  myDiagram.layout = new go.TreeLayout();
}
function setCircularLayout() {
  myDiagram.layout = new go.CircularLayout();
}

//see https://gojs.net/latest/samples/relationships.html for others
let dottedBlackLine = new go.Shape({
  geometryString: "M0 0 M4 0 L4.1 0",
  fill: "transparent",
  stroke: "black",
  strokeWidth: 1,
  strokeCap: "round"
});

let singleBlackLine= new go.Shape({
  geometryString: "M0 0 L1 0",
  fill: "transparent",
  stroke: "black",
  strokeWidth: 1,
  strokeCap: "square"
});

let singleGreenLine= new go.Shape({
  geometryString: "M0 0 L1 0",
  fill: "transparent",
  stroke: "limegreen",
  strokeWidth: 1,
  strokeCap: "square"
});

function patternForScope(scope) {
  console.log(`patternForScope ${scope}`)
  switch(scope) {
    case "optional": console.log("returning optional"); return dottedBlackLine;
    case "test": console.log("returning test"); return singleGreenLine;
    default: console.log("returning default"); return singleBlackLine;
  }
}
myDiagram.linkTemplate =
    new go.Link("Auto")
    .add(
        new go.Shape({ isPanelMain: true, stroke: "transparent" })
            .bind("pathPattern", "scope", patternForScope),
        new go.Shape({ toArrow: "Standard" })
    );

myDiagram.groupTemplate =
    new go.Group("Auto")
    .add(
        new go.Shape(
            "Rectangle",
            {
              name: "OBJSHAPE",
              parameter1: 14,
              fill: "rgba(0,139,139,0.10)"
            }
        )
    )
    .add(
        new go.Panel("Vertical")
        .add(
            new go.TextBlock(
                { margin: 8, font: "bold 14px sans-serif", stroke: '#333' }
            ).bind("text", "fullName")
        )
        .add(
            new go.Placeholder(
                { padding: 5}
            )
        )
    );

myDiagram.nodeTemplate =
    new go.Node("Auto")
    .add(
        new go.Shape(
            "RoundedRectangle",
            { strokeWidth: 1, fill: "white" }
        )
    )
    .add(
        new go.Panel("Vertical")
      .add(
          new go.TextBlock(
              { margin: 2, font: "bold 14px sans-serif", stroke: '#333' }
          ).bind("text", "org")
      )
      .add(
          new go.TextBlock(
              { margin: 2, font: "bold 14px sans-serif", stroke: '#333' }
          ).bind("text", "name")
      )
      .add(
          new go.TextBlock(
              { margin: 2, font: "bold 14px sans-serif", stroke: '#333' }
          ).bind("text", "version")
      )
    );


async function loadJSONData(path) {
  const response = await fetch(path);
  const jsonData = await response.json();
  console.log(jsonData);
  myDiagram.model = new go.GraphLinksModel(jsonData["nodes"], jsonData["links"]);
}
