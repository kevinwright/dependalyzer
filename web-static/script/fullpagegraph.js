const myDiagram = new go.Diagram(
    myDiagramDiv,
    {
      initialAutoScale: go.Diagram.Uniform,
      //layout: new go.ForceDirectedLayout(),
      layout: new go.LayeredDigraphLayout(),
      //layout: new go.TreeLayout(),
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

myDiagram.linkTemplate =
    new go.Link("Auto")
    .add(
        new go.Shape().bind("stroke", "scope", x => x === "test" ? "blue" : "black"),
        new go.Shape({ toArrow: "Standard" }),
        new go.Panel("Vertical")
        .add(
            new go.TextBlock({ margin: 3 }).bind("text", "relType"),
            new go.TextBlock({ margin: 3 }).bind("text", "scope")
        )
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
