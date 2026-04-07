# 🚀 Enhanced McFeels E-commerce Automation Framework

## 📋 Overview

This is a comprehensive **Selenium WebDriver automation framework** for testing the McFeels e-commerce website. The framework features **random test data generation**, **advanced HTML reporting**, **wishlist functionality testing**, and **comprehensive test coverage**.

## 🌟 Key Features

### 🎲 **Smart Random Testing**
- **Dynamic Category Selection**: Randomly selects product categories
- **Random Product Testing**: Picks products dynamically for realistic testing  
- **Realistic Test Data**: Generates random user data (names, emails, addresses, phone numbers)
- **Random Search Terms**: Tests various search scenarios automatically

### 🛍️ **Comprehensive Test Coverage**
- ✅ **Homepage Testing**: Logo visibility, navigation, search functionality
- ✅ **User Registration**: Account creation and authentication flows
- ✅ **Product Navigation**: Category browsing, product selection
- ✅ **Wishlist Testing**: Add to wishlist, wishlist management
- ✅ **Shopping Cart**: Add to cart, quantity updates, coupon application
- ✅ **Search & Filters**: Valid/invalid searches, product sorting
- ✅ **Checkout Process**: Form filling with random data, user login
- ✅ **Responsive Testing**: Mobile, tablet, and desktop views

### 📊 **Advanced Reporting**
- **Interactive HTML Reports** with charts and analytics
- **Real-time Dashboard** with auto-refresh
- **Screenshot Gallery** with organized captures
- **CSV Export** for data analysis
- **Test Statistics** with pass/fail rates
- **Execution Timeline** tracking

### 🗂️ **Organized Structure**
- **Date-based folders** for screenshots and reports
- **Cloud upload** to ImgBB for external sharing
- **Enhanced CSV** with comprehensive test data
- **Clean project structure** with Maven management

## 🏗️ Project Structure

```
Mcfeels/
├── src/main/java/Mcfeels/
│   ├── mcfeels.java              # Main automation class
│   ├── TestDataManager.java      # Random test data generator  
│   ├── EnhancedHTMLReporter.java # Interactive report generator
│   └── TestResult.java           # Test result data model
├── pom.xml                       # Maven dependencies
└── README.md                     # This documentation
```

## 🛠️ Setup & Installation

### Prerequisites
- ✅ **Java 8+** 
- ✅ **Maven 3.6+**
- ✅ **Chrome Browser** (latest version)
- ✅ **ChromeDriver** (auto-managed by WebDriverManager)

### Quick Start

1. **Clone/Download the project**
   ```bash
   git clone <your-repository>
   cd Mcfeels
   ```

2. **Compile the project**
   ```bash
   mvn clean compile
   ```

3. **Run the automation**
   ```bash
   mvn exec:java -Dexec.mainClass="Mcfeels.mcfeels"
   ```

   Or create a JAR and run:
   ```bash
   mvn package
   java -jar target/mcfeels-automation-2.0.0.jar
   ```

## 🎯 Test Scenarios Covered

| Test Category | Description | Features |
|---------------|-------------|----------|
| **Homepage** | Basic navigation and functionality | Logo visibility, search box, popup handling |
| **User Registration** | Account creation flows | Registration forms, authentication |
| **Product Navigation** | Dynamic product browsing | Random category selection, product details |
| **Wishlist** | Wishlist management | Add to wishlist, wishlist navigation |
| **Shopping Cart** | Cart operations | Add to cart, quantity updates, coupons |
| **Search & Filters** | Search functionality | Valid/invalid searches, sorting, filtering |
| **Checkout** | Purchase flow | Form filling, user login, address entry |
| **UI/UX** | User interface testing | Responsive design, navigation elements |

## 📊 Generated Reports

### 1. **Enhanced HTML Report**
- 📍 Location: `C:\Users\deepa\Documents\Automation\Mcfeels\html\[date]\[time]\enhanced-test-report.html`
- 🌟 Features: Interactive charts, navigation, analytics

### 2. **Quick Dashboard**  
- 📍 Location: `C:\Users\deepa\Documents\Automation\Mcfeels\html\[date]\[time]\dashboard.html`
- 🔄 Auto-refreshes every 30 seconds

### 3. **CSV Data Export**
- 📍 Location: `C:\Users\deepa\Documents\Automation\Mcfeels\common_results.csv`
- 📈 Contains: Test steps, screenshots, categories, products, timestamps

### 4. **Screenshot Gallery**
- 📍 Location: `C:\Users\deepa\Documents\Automation\Mcfeels\screenshots\[date]\[time]\`
- 📸 Organized by date and time with descriptive names

## 🎲 Random Test Data Examples

The framework automatically generates realistic test data:

```
👤 User Info:
   Name: Sarah Johnson
   Email: sarah.johnson834@exinent.com
   Company: Innovation Labs
   Phone: (555) 123-4567

🏠 Address Info:
   Address: 1234 Oak Ave
   City: Phoenix
   Zip: 85001

🛍️ Test Scenarios:
   Category: Cabinet Hardware  
   Search Term: hinges
   Coupon: SAVE15
   Sort By: price
```

## ⚙️ Configuration

### Key Settings in `mcfeels.java`:

```java
// Change these paths as needed
static String ROOT_PATH = "C:\\Users\\deepa\\Documents\\Automation\\Mcfeels";
static String IMGBB_API_KEY = "your-api-key-here";

// Test data is auto-generated, no configuration needed
```

## 🔍 Troubleshooting

### Common Issues & Solutions

**Issue**: ChromeDriver not found
**Solution**: The framework uses WebDriverManager for automatic driver management

**Issue**: Screenshots not saving  
**Solution**: Check folder permissions for the ROOT_PATH directory

**Issue**: ImgBB upload failing
**Solution**: Verify IMGBB_API_KEY or disable cloud upload in code

**Issue**: Tests running too fast
**Solution**: Adjust wait times in individual test methods

## 📈 Performance & Analytics

### Test Execution Metrics:
- ⏱️ **Average Runtime**: 3-5 minutes for complete suite
- 📊 **Test Coverage**: 8+ major e-commerce scenarios  
- 🎯 **Pass Rate Tracking**: Automatic calculation and reporting
- 📸 **Screenshot Capture**: 15-25+ screenshots per run

### Scalability Features:
- 🔄 **Parallel Execution Ready**: Framework supports TestNG integration
- 📦 **Docker Compatible**: Can be containerized for CI/CD
- 🌐 **Cross-browser Ready**: Easy to extend for Firefox, Edge, Safari
- 📊 **CI/CD Integration**: Maven-based for Jenkins, GitHub Actions

## 🚀 Future Enhancements

### Planned Features:
- [ ] **API Testing Integration** (REST Assured)
- [ ] **Database Validation** (JDBC integration)  
- [ ] **Performance Testing** (JMeter integration)
- [ ] **Visual Testing** (Applitools/Percy integration)
- [ ] **Mobile Testing** (Appium integration)
- [ ] **Email Verification** (Gmail API)

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)  
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For issues, questions, or contributions:
- 📧 **Email**: deepak.maheshwari@exinent.com
- 📱 **Project**: McFeels E-commerce Automation
- 🏢 **Company**: Exinent LLC

---

## 🎊 Success Indicators

When you run the automation, you'll see:

```bash
🚀 Starting Enhanced McFeels E-commerce Automation...
📋 Test Plan: Homepage → Registration → Product Browse → Wishlist → Cart → Search → Checkout

🎲 Generated Test Data:
========================
👤 User Info:
   Name: Michael Davis
   Email: michael.davis123@exinent.com
   Company: Tech Solutions Inc
   Phone: (555) 987-6543

🏠 Starting Homepage Test...
✅ Logo is visible
✅ Search box is functional  
✅ Homepage test completed

🛍️ Starting Enhanced Product Flow...
🎯 Random category selected: Screws & Fasteners
🎯 Selected product: 8 x 1-3/8 in. ProMax Flat Head Wood Screw
✅ Product page loaded

❤️ Starting Wishlist Test...
✅ Product added to wishlist
✅ Wishlist page accessed

🛒 Starting Cart Management Test...
✅ Added to cart
✅ Mini cart opened
✅ Cart quantity updated
✅ Coupon applied

🔍 Starting Enhanced Search and Filter Test...  
🎯 Random search term: hinges
✅ Search test for 'hinges' completed
✅ Invalid search test completed

💳 Starting enhanced checkout flow...
✅ Checkout form filled with test data

🧭 Starting UI Navigation Test...
✅ Footer navigation visible
✅ Found 12 navigation links
✅ UI Navigation test completed

🎊 Enhanced McFeels automation completed successfully!
⏱️ Total execution time: 245 seconds

📊 Results saved to organized folders:
   📸 Screenshots: screenshots\2026-03-09\19-25-30
   📄 HTML Report: html\2026-03-09\19-25-30\enhanced-test-report.html  
   📊 CSV Data: common_results.csv
🌐 Check results in: C:\Users\deepa\Documents\Automation\Mcfeels
```

---

**Built with ❤️ for comprehensive e-commerce testing automation**