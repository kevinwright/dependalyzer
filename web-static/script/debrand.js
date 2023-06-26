CanvasRenderingContext2D.prototype._fillText = CanvasRenderingContext2D.prototype.fillText;

// SECTION: Patch the HTML5 'canvas' element methods.
CanvasRenderingContext2D.prototype.fillText = function(text, x, y) {
  replacedText = text;
  switch (text) {
    case 'GoJS 2.3 evaluation':
    case '(c) 1998-2023 Northwoods Software':
    case 'Not for distribution or production use':
    case 'gojs.net':
      replacedText = '';
  }
  return this._fillText(replacedText, x, y);
}