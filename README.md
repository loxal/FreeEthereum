**Support this work**
<!-- BADGES/ -->
<span class="badge-paypal">
<a href="https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&amp;hosted_button_id=MA847TR65D4N2" title="Donate to this project using PayPal">
<img src="https://img.shields.io/badge/paypal-donate-yellow.svg" alt="PayPal Donate"/>
</a></span>
<span class="badge-flattr">
<a href="https://flattr.com/submit/auto?fid=o6ok7n&url=https%3A%2F%2Fgithub.com%2Floxal" title="Donate to this project using Flattr">
<img src="https://img.shields.io/badge/flattr-donate-yellow.svg" alt="Flattr Donate" />
</a></span>
<span class="badge-gratipay"><a href="https://gratipay.com/~loxal" title="Donate weekly to this project using Gratipay">
<img src="https://img.shields.io/badge/gratipay-donate-yellow.svg" alt="Gratipay Donate" />
</a></span>
<!-- /BADGES -->

[Support this work with crypto-currencies like BitCoin, Ethereum, Ardor, and Komodo!](http://me.loxal.net/coin-support.html)


FreeEthereum
=

This is the last commit of the MIT-licensed version of the Java implementation of the Ethereum protocol.
The original EthereumJ Ethereum implemetation is now licensed under a commercially problematic GPL v3 license. This version is supposed to stay MIT.

# Key differentiators compared to EthereumJ

FreeEthereum | EthereumJ
--- | --- 
MIT License | GPL v3 License
Java 8 (porting to Kotlin) | Java 7 

##### Building an executable JAR
```
git clone https://github.com/ethereum/ethereumj
cd ethereumj
cp ethereumj-core/src/main/resources/ethereumj.conf ethereumj-core/src/main/resources/user.conf
vim ethereumj-core/src/main/resources/user.conf # adjust user.conf to your needs
./gradlew clean shadowJar
java -jar ethereumj-core/build/libs/ethereumj-core-*-all.jar
```

- build a standalone executable jar with `../gradlew shadow` and execute the `-all` jar in `build/libs` using `java -jar [jarfile]`.

##### Optional samples to try:
```
./gradlew run -PmainClass=org.ethereum.samples.BasicSample
./gradlew run -PmainClass=org.ethereum.samples.FollowAccount
./gradlew run -PmainClass=org.ethereum.samples.PendingStateSample
./gradlew run -PmainClass=org.ethereum.samples.PriceFeedSample
./gradlew run -PmainClass=org.ethereum.samples.PrivateMinerSample
./gradlew run -PmainClass=org.ethereum.samples.TestNetSample
./gradlew run -PmainClass=org.ethereum.samples.TransactionBomb
```

# Configuring FreeEthereum

For reference on all existing options, their description and defaults you may refer to the default config `ethereumj.conf` (you may find it in either the library jar or in the source tree `ethereum-core/src/main/resources`) 
To override needed options you may use one of the following ways: 
* put your options to the `<working dir>/config/ethereumj.conf` file
* put `user.conf` to the root of your classpath (as a resource) 
* put your options to any file and supply it via `-Dethereumj.conf.file=<your config>`
* programmatically by using `SystemProperties.CONFIG.override*()`
* programmatically using by overriding Spring `SystemProperties` bean 

Note that don’t need to put all the options to your custom config, just those you want to override. 


