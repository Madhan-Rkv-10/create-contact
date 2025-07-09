import UIKit
import Flutter
import ContactsUI

@main
@objc class AppDelegate: FlutterAppDelegate, CNContactViewControllerDelegate {
    var flutterResult: FlutterResult?

    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let controller: FlutterViewController = window?.rootViewController as! FlutterViewController
        let channel = FlutterMethodChannel(name: "contact_launcher", binaryMessenger: controller.binaryMessenger)

        channel.setMethodCallHandler { [weak self] (call: FlutterMethodCall, result: @escaping FlutterResult) in
            guard call.method == "createContact" else {
                result(FlutterMethodNotImplemented)
                return
            }

            guard let args = call.arguments as? [String: Any] else {
                result(FlutterError(code: "INVALID_ARGUMENT", message: "Expected arguments", details: nil))
                return
            }

            self?.flutterResult = result
            self?.showNewContact(args: args)
        }

        GeneratedPluginRegistrant.register(with: self)
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }

    private func showNewContact(args: [String: Any]) {
        let contact = CNMutableContact()

        // Basic Info
        contact.givenName = args["firstName"] as? String ?? ""
        contact.familyName = args["lastName"] as? String ?? ""
        contact.organizationName = args["company"] as? String ?? ""
        contact.note = args["verificationCode"] as? String ?? ""
        contact.phoneticGivenName = args["pronunciation"] as? String ?? ""

        // Phone Numbers
        var phoneList = [CNLabeledValue<CNPhoneNumber>]()
        if let phone1 = args["primaryPhone"] as? String {
            phoneList.append(CNLabeledValue(label: CNLabelPhoneNumberMobile, value: CNPhoneNumber(stringValue: phone1)))
        }
        if let phone2 = args["secondaryPhone"] as? String {
            phoneList.append(CNLabeledValue(label: CNLabelPhoneNumberiPhone, value: CNPhoneNumber(stringValue: phone2)))
        }
        contact.phoneNumbers = phoneList

        // Emails
        if let email = args["email"] as? String {
            contact.emailAddresses = [CNLabeledValue(label: CNLabelWork, value: email as NSString)]
        }

        // URL
        if let url = args["url"] as? String {
            contact.urlAddresses = [CNLabeledValue(label: CNLabelURLAddressHomePage, value: url as NSString)]
        }

        // Birthday
        if let birthMap = args["birthday"] as? [String: Int],
           let year = birthMap["year"], let month = birthMap["month"], let day = birthMap["day"] {
            contact.birthday = DateComponents(calendar: .current, year: year, month: month, day: day)
        }

        // Address
        if let address = args["address"] as? [String: String] {
            let postal = CNMutablePostalAddress()
            postal.street = address["street"] ?? ""
            postal.city = address["city"] ?? ""
            postal.state = address["state"] ?? ""
            postal.postalCode = address["postalCode"] ?? ""
            postal.country = address["country"] ?? ""
            contact.postalAddresses = [CNLabeledValue(label: CNLabelHome, value: postal)]
        }

        // Related Name
        if let relatedName = args["relatedName"] as? String {
            contact.contactRelations = [
                CNLabeledValue(label: CNLabelContactRelationFather, value: CNContactRelation(name: relatedName))
            ]
        }

        // Social Profile
        if let socialName = args["socialProfile"] as? String {
            let profile = CNSocialProfile(urlString: nil, username: socialName, userIdentifier: nil, service: CNSocialProfileServiceTwitter)
            contact.socialProfiles = [CNLabeledValue(label: CNLabelWork, value: profile)]
        }

        // Instant Message
        if let imName = args["instantMessage"] as? String {
            let im = CNInstantMessageAddress(username: imName, service: "WhatsApp")
            contact.instantMessageAddresses = [CNLabeledValue(label: CNLabelWork, value: im)]
        }

        // Photo
        if let photoBytes = args["photo"] as? FlutterStandardTypedData {
            contact.imageData = photoBytes.data
        }

        // Presentation
        let fullscreen = args["fullscreen"] as? Bool ?? true
        let controller = CNContactViewController(forNewContact: contact)
        controller.delegate = self
        controller.contactStore = CNContactStore()
        controller.modalPresentationStyle = fullscreen ? .fullScreen : .pageSheet

        let nav = UINavigationController(rootViewController: controller)
        nav.modalPresentationStyle = fullscreen ? .fullScreen : .pageSheet

        if let rootVC = window?.rootViewController {
            rootVC.present(nav, animated: true)
        } else {
            flutterResult?(FlutterError(code: "NO_UI", message: "Cannot present contact view", details: nil))
            flutterResult = nil
        }
    }

    // MARK: - CNContactViewControllerDelegate
    func contactViewController(_ viewController: CNContactViewController, didCompleteWith contact: CNContact?) {
        viewController.dismiss(animated: true) { [weak self] in
            guard let self = self else { return }

            if let contact = contact {
                let fullName = "\(contact.givenName) \(contact.familyName)".trimmingCharacters(in: .whitespaces)

                let phoneNumbers = contact.phoneNumbers.map { $0.value.stringValue }
                let emails = contact.emailAddresses.map { $0.value as String }
                let urls = contact.urlAddresses.map { $0.value as String }
                let socialProfiles = contact.socialProfiles.map {
                    ($0.value as CNSocialProfile).username ?? ""
                }
                let imAddresses = contact.instantMessageAddresses.map {
                    ($0.value as CNInstantMessageAddress).username
                }
                let relatedNames = contact.contactRelations.map {
                    $0.value.name
                }

                var addresses: [[String: String]] = []
                for labeled in contact.postalAddresses {
                    let postal = labeled.value
                    addresses.append([
                        "street": postal.street,
                        "city": postal.city,
                        "state": postal.state,
                        "postalCode": postal.postalCode,
                        "country": postal.country
                    ])
                }

                var birthday: [String: Int]? = nil
                if let bday = contact.birthday {
                    birthday = [
                        "year": bday.year ?? 0,
                        "month": bday.month ?? 0,
                        "day": bday.day ?? 0
                    ]
                }

                let response: [String: Any] = [
                    "status": ContactCreationResult.created.rawValue,
                    "givenName": contact.givenName,
                    "familyName": contact.familyName,
                    "fullName": fullName,
                    "company": contact.organizationName,
                    "email": emails,
                    "phoneNumbers": phoneNumbers,
                    "addresses": addresses,
                    "birthday": birthday as Any,
                    "socialProfiles": socialProfiles,
                    "instantMessages": imAddresses,
                    "relatedNames": relatedNames,
                    "urlAddresses": urls
                ]

                self.flutterResult?(response)
            } else {
                self.flutterResult?([
                    "status": ContactCreationResult.cancelled.rawValue
                ])
            }

            self.flutterResult = nil
        }
    }
}

enum ContactCreationResult: String {
    case cancelled = "USER_CANCELLED"
    case created = "CREATED"
}
