use 5.012;
use warnings;

use Test::More;
use Core::Universal;
use Spot::Universal;
use FindBin;

$FindBin::Bin =~ s'/t''; #hack for find bin

ok(Core::Universal->init(), 'init ok');

my %list = Core::Universal->list();
my @list = keys %list;
ok(scalar @list, 'list is not empty');
my %id;
for my $i (@list) {
	like($i,qr/^\w+$/,"ids are only word characters: $i");
	ok(!exists $id{lc $i},'no case insensitive matching ids: '. $i);
	$id{lc $i} = 1;
	ok(Core::Universal->known($i), 'core knows all listed ids: ' . $i);
}

ok(! Core::Universal->list_need_info(), 'universal never needs more info');


my $remote = new_ok('Core::Universal', [$list[0]], $list[0]);
my @about = $remote->about();
ok(0+@about,'about returned something '. explain \@about);
isa_ok($about[0],'ARRAY','first about is array ref');
ok($remote->fetch_info(),'fetch info returned ok ' . $list[0]);
is($remote->name(),$list{$list[0]}, 'names match '. $list[0]);

my $spot = $remote->first();
isa_ok($spot,'Spot::Universal');

done_testing(9 + 3*@list);
