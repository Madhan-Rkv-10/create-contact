import Flutter
import UIKit
import ContactsUI
@main
@objc class AppDelegate: FlutterAppDelegate {
  var flutterResult: FlutterResult?
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)

    let controller = window?.rootViewController as! FlutterViewController
        let channel = FlutterMethodChannel(name: "contact_launcher", binaryMessenger: controller.binaryMessenger)
 
        channel.setMethodCallHandler { [weak self] call, result in
            if call.method == "createContact" {
                guard let args = call.arguments as? [String: Any],
                      let number = args["number"] as? String else {
                    result(FlutterError(code: "INVALID_ARGUMENT", message: "Expected number", details: nil))
                    return
                }
 
                self?.flutterResult = result
                self?.showNewContact(number: number)
            } else {
                result(FlutterMethodNotImplemented)
            }
        }
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
     private func showNewContact(number: String) {
        let contact = CNMutableContact()
        contact.phoneNumbers = [CNLabeledValue(label: CNLabelPhoneNumberMobile, value: CNPhoneNumber(stringValue: number))]
        let controller = CNContactViewController(forNewContact: contact)
        controller.delegate = self as? CNContactViewControllerDelegate
        controller.contactStore = CNContactStore()
        let nav = UINavigationController(rootViewController: controller)
        window?.rootViewController?.present(nav, animated: true)
    }
 
    func contactViewController(_ viewController: CNContactViewController, didCompleteWith contact: CNContact?) {
            print("✅ iOS contactViewController didCompleteWith called. Contact: \(String(describing: contact))")
        viewController.dismiss(animated: true)
          flutterResult?(contact != nil)
          flutterResult = nil
    }
}