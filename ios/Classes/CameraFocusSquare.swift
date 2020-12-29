//
//  CameraFocusSquare.swift
//  adv_camera
//
//  Created by richardo thayeb on 12/29/20.
//

import Foundation

class CameraFocusSquare: UIView,CAAnimationDelegate {

    internal let kSelectionAnimation:String = "selectionAnimation"

    fileprivate var _selectionBlink: CABasicAnimation?
    
    var borderColor : UIColor!
    var borderWidth : CGFloat!

    convenience init(touchPoint: CGPoint, borderColor: UIColor!, borderWidth: CGFloat!) {
        self.init()
        self.borderColor = borderColor
        self.borderWidth = borderWidth
        self.updatePoint(touchPoint)
        self.backgroundColor = UIColor.clear
        self.layer.borderWidth = 2.0
        self.layer.borderColor = self.borderColor.cgColor
        initBlink()
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
    }

    fileprivate func initBlink() {
        // create the blink animation
        self._selectionBlink = CABasicAnimation(keyPath: "borderColor")
        self._selectionBlink!.toValue = (self.borderColor.cgColor as AnyObject)
        self._selectionBlink!.repeatCount = 3
        // number of blinks
        self._selectionBlink!.duration = 0.4
        // this is duration per blink
        self._selectionBlink!.delegate = self
    }



    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    /**
     Updates the location of the view based on the incoming touchPoint.
     */

    func updatePoint(_ touchPoint: CGPoint) {
        let squareWidth: CGFloat = self.borderWidth
        let frame: CGRect = CGRect(x: touchPoint.x - squareWidth / 2, y: touchPoint.y - squareWidth / 2, width: squareWidth, height: squareWidth)
        self.frame = frame
    }
    /**
     This unhides the view and initiates the animation by adding it to the layer.
     */

    func animateFocusingAction() {

        if let blink = _selectionBlink {
            // make the view visible
            self.alpha = 1.0
            self.isHidden = false
            // initiate the animation
            self.layer.add(blink, forKey: kSelectionAnimation)
        }

    }
    /**
     Hides the view after the animation stops. Since the animation is automatically removed, we don't need to do anything else here.
     */

    public func animationDidStop(_ anim: CAAnimation, finished flag: Bool){
        if flag{
            // hide the view
            self.alpha = 0.0
            self.isHidden = true
        }
    }

}
